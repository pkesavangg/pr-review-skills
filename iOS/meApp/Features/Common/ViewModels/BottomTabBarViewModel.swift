//
//  BottomTabBarViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import Foundation
import SwiftUI
import Combine

@MainActor
class BottomTabBarViewModel: ObservableObject {
    @Injector var feedService: FeedService
    // Inject Bluetooth service to listen for new scale discovery events
    @Injector var bluetoothService: BluetoothService
    // Publisher-driven sheet presentation for newly discovered scales
    @Published var discoveredScale: Device? = nil
    /// Holds the most recent Bluetooth discovery event used by the *Scale Discovered* sheet.
    @Published var discoveryEvent: DeviceDiscoveryEvent? = nil
    @Published var selectedTab: BottomTab = .dash
    @Published var showSettingsBadge: Bool = false
    @Published var showAppSync: Bool = false
    @Published var showTabBar: Bool = true
    /// Holds the body-composition metrics captured by AppSync when the user taps **Edit** on the confirmation card.
    /// `ManualEntryScreen` will consume this to pre-populate its form, then reset it to `nil`.
    @Published var pendingAppSyncEditMetrics: AppSyncEntryMetrics? = nil
    /// Holds a pending navigation request to be performed by `SettingsScreen` once it appears.
    /// This is set when the user taps *Connect* in the *Add Apple Health Integration* modal.
    @Published var pendingSettingsNavigation: SettingsRoute? = nil
    @Published var setupPayload: ScaleDiscoverSheetInfo? = nil
    
    // MARK: - Dependencies
    @Injector private var healthKitService: HealthKitService
    @Injector private var notificationService: NotificationHelperService
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService
    // New dependency to evaluate permission status
    @Injector private var permissionsService: PermissionsService

    // MARK: - Permission Disabled Alert Tracking
    /// Indicates whether the *Permission Disabled* alert has already been shown in the current app session.
    private var hasShownPermissionAlert: Bool = false
    
    private let toastLang = ToastStrings.self
    private let tag = "BottomTabBarViewModel"
    private var cancellables: Set<AnyCancellable> = []
    
    init() {
        self.showSettingsBadge = feedService.getUnreadFeedCount() > 0
        // Subscribe to Bluetooth discovery events to surface the half-sheet when appropriate
        bluetoothService.deviceDiscoveredPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                guard let self else { return }
                if self.shouldShowDiscoveredScale(for: event) {
                    self.discoveredScale = event.device
                    self.discoveryEvent = event
                }
            }
            .store(in: &cancellables)
        
        bluetoothService.newEntryReceivedPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] event in
                guard let self else { return }
                if !self.bluetoothService.isSetupInProgress {
                    notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
                }
            }
            .store(in: &cancellables)
        
        // Perform Apple Health integration check on launch
        Task { [weak self] in
            await self?.checkAppleHealthIntegrationStatus()
        }
        
        // Update the app sync tab based on the app sync scale defined in the paired scale list
        scaleService.$scales
            .map { scales in
                scales.contains { $0.bathScale?.scaleType == ScaleSourceType.appsync.rawValue }
            }
            .receive(on: DispatchQueue.main)
            .assign(to: \.showAppSync, on: self)
            .store(in: &cancellables)

        // Observe permission/state changes to decide when to show the *Permission Disabled* alert.
        permissionsService.$requiredCategories
            .combineLatest(permissionsService.$permissions)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _, _ in
                self?.evaluateAndShowPermissionAlert()
            }
            .store(in: &cancellables)
    }

    // MARK: - Permission Disabled Alert Helpers
    /// Evaluates whether the *Permission Disabled* alert should be shown and presents it if needed.
    private func evaluateAndShowPermissionAlert() {
        guard !hasShownPermissionAlert else { return }
        // Show alert only if Bluetooth is a required permission
        guard permissionsService.requiredCategories.contains(.bluetooth) else { return }
        // Check the current Bluetooth permission states (switch & auth)
        let btSwitchState = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) ?? .ENABLED
        let btAuthState   = permissionsService.getPermissionState(.BLUETOOTH) ?? .ENABLED
        // Alert only when either state is disabled
        guard btSwitchState == .DISABLED || btAuthState == .DISABLED else { return }

        showPermissionDisabledAlert()
    }

    /// Presents the *Permission Disabled* alert and handles navigation when the user taps **APP PERMISSION**.
    private func showPermissionDisabledAlert() {
        let lang = AlertStrings.PermissionDisabledAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.dismissButton, type: .secondary) { _ in },
                AlertButtonModel(title: lang.appPermissionButton, type: .primary) { [weak self] _ in
                    self?.navigateToAppPermissions()
                }
            ]
        )

        notificationService.showAlert(alert)
        hasShownPermissionAlert = true
    }

    /// Selects the **Settings** tab and navigates to the App Permissions screen.
    private func navigateToAppPermissions() {
        selectTab(.settings)
        pendingSettingsNavigation = .appPermissions
    }
    
    // MARK: - Tab Deactivation Handling
    /// A dictionary holding async deactivation handlers for each tab. A handler should return `true` if it is safe
    /// to leave the current tab, or `false` to cancel navigation. Views are responsible for registering and removing
    /// their own handlers via `registerDeactivationHandler` / `removeDeactivationHandler`.
    private var deactivationHandlers: [BottomTab: () async -> Bool] = [:]
    
    /// Registers a de-activation handler for the given tab, overriding any existing handler.
    /// - Parameters:
    ///   - tab:     The `BottomTab` for which the handler applies.
    ///   - handler: An async closure returning a `Bool` indicating whether the tab can be left.
    func registerDeactivationHandler(for tab: BottomTab, handler: @escaping () async -> Bool) {
        deactivationHandlers[tab] = handler
    }
    
    /// Removes any previously registered de-activation handler for the given tab.
    /// - Parameter tab: The `BottomTab` whose handler should be removed.
    func removeDeactivationHandler(for tab: BottomTab) {
        deactivationHandlers.removeValue(forKey: tab)
    }
    
    /// Returns the currently registered de-activation handler for a tab, if available.
    /// - Parameter tab: The tab whose handler is requested.
    func deactivationHandler(for tab: BottomTab) -> (() async -> Bool)? {
        deactivationHandlers[tab]
    }
    
    var visibleTabs: [BottomTab] {
        var tabs: [BottomTab] = [.dash, .entry, .history, .settings]
        if showAppSync {
            tabs.append(.appsync)
        }
        return tabs
    }
    
    func selectTab(_ tab: BottomTab) {
        selectedTab = tab
    }
    
    /// Navigates to the goal setting screen via the settings tab
    func navigateToGoalSetting() {
        selectTab(.settings)
        pendingSettingsNavigation = .goal
    }
    
    /// Dismisses the “Scale Discovered” sheet.
    func dismissDiscoveredScaleSheet() {
        discoveredScale = nil
        discoveryEvent = nil
    }
    
    // MARK: - Connect Action from Scale Discovered Sheet
    func openScaleSetup(scale: Device, event: DeviceDiscoveryEvent?) {
        let sku = scale.sku ?? event?.deviceInfo.sku ?? ""
        guard !sku.isEmpty, let setupType = event?.deviceInfo.setupType else { return }
        bluetoothService.isSetupInProgress = true
        
        switch setupType {
        case .lcbt, .btWifiR4:
            setupPayload = ScaleDiscoverSheetInfo(sku: sku, scale: scale, event: event)
        default:
            // Handle other setup types if needed
            bluetoothService.isSetupInProgress = false
            return
        }
        
        // Ensure sheet dismissed
        dismissDiscoveredScaleSheet()
    }
    
    // MARK: - Apple Health Integration Prompt
    /// Checks whether the user should be prompted to add Apple Health integration
    /// and shows the modal if needed.
    private func checkAppleHealthIntegrationStatus() async {
        do {
            if let modalState = try await healthKitService.shouldShowHKIntegrationModal() {
                await MainActor.run { [weak self] in
                    switch modalState {
                    case .addIntegration, .finishAdding:
                        self?.presentHKIntegrationModal(for: modalState)
                    case .outOfSync:
                        self?.presentHKIntegrationModal(for: .outOfSync)
                    }
                }
            }
        } catch {
            // Silently ignore – logging is handled in `HealthKitService`
        }
    }
    
    /// Presents the Apple Health Integration modal based on the given state.
    private func presentHKIntegrationModal(for state: HKIntegrationModalState) {
        // Configure actions based on modal state
        let onPrimary: () -> Void
        let onSecondary: (() -> Void)?
        
        switch state {
        case .addIntegration, .finishAdding:
            onPrimary = { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                // Switch to Settings tab and queue navigation to the Integrations screen.
                self.selectTab(.settings)
                self.pendingSettingsNavigation = .integrations
            }
            onSecondary = nil
            
        case .outOfSync:
            onPrimary = { [weak self] in
                guard let self else { return }
                self.healthKitService.openAppleHealth()
                self.notificationService.dismissModal()
            }
            
            onSecondary = { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                Task {
                    self.notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration))
                    do {
                        try await self.healthKitService.clearHealthKit()
                        self.notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationRemoved))
                    } catch {
                        self.logger.log(level: .error, tag: self.tag, message: "Failed to clear HealthKit data", data: error.localizedDescription)
                    }
                    self.notificationService.dismissLoader()
                }
            }
        }
        
        let modalView = HKIntegrationModalView(
            state: state,
            onClose: { [weak notificationService] in
                notificationService?.dismissModal()
            },
            onPrimaryTap: onPrimary,
            onSecondaryTap: onSecondary
        )
        
        let modalData = ModalData(presentedView: AnyView(modalView))
        notificationService.showModal(modalData)
    }
    
    // MARK: - Scale Discovery Handling
    private func shouldShowDiscoveredScale(for event: DeviceDiscoveryEvent) -> Bool {
        /// Checks if the scale discovery event should trigger the "Scale Discovered" sheet.
        guard !bluetoothService.isSetupInProgress,
              bluetoothService.canShowScaleDiscoveredModal,
              !(bluetoothService.skipDevices.contains(event.device.broadcastIdString ?? "")),
              event.isNew,
              discoveredScale == nil,
              !event.deviceInfo.sku.isEmpty else {
            return false
        }
        return true
    }
}
