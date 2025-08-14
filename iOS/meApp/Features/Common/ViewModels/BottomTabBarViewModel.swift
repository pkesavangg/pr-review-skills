//
//  BottomTabBarViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import Foundation
import SwiftUI
import Combine
import GGBluetoothSwiftPackage

@MainActor
class BottomTabBarViewModel: ObservableObject {
    @Injector var feedService: FeedService
    // Inject Bluetooth service to listen for new scale discovery events
    @Injector var bluetoothService: BluetoothService
    // Inject GoalAlertService to handle goal alert navigation
    @Injector var goalAlertService: GoalAlertService
    // Publisher-driven sheet presentation for newly discovered scales
    @Published var discoveredScale: Device? = nil
    /// Holds the most recent Bluetooth discovery event used by the *Scale Discovered* sheet.
    @Published var discoveryEvent: DeviceDiscoveryEvent? = nil
    @Published var selectedTab: BottomTab = .dash
    @Published var canShowFeedNotificationBadge: Bool = false
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
    // New dependencies for Set Goal Card logic
    @Injector private var entryService: EntryService
    @Injector private var accountService: AccountService
    @Injector private var scaleService: ScaleService
    // New dependency to evaluate permission status
    @Injector private var permissionsService: PermissionsService
    @Injector private var pushNotificationService: PushNotificationService
    
    // MARK: - Permission Disabled Alert Tracking
    /// Indicates whether the *Permission Disabled* alert has already been shown in the current app session.
    private var hasShownPermissionAlert: Bool = false
    // MARK: - Goal Card Tracking
    /// Keeps track if the Set a Goal card has been shown in this app session.
    private var hasShownSetGoalCardThisSession: Bool = false
    private var notificationOnlyAlertShown: Bool {
        get {
            guard let accountId = accountService.activeAccount?.accountId else { return false }
            let key = KvStorageKeys.notificationOnlyPermAlertShownKey(for: accountId)
            return (KvStorageService.shared.getValue(forKey: key) as? Bool) ?? false
        }
        set {
            guard let accountId = accountService.activeAccount?.accountId else { return }
            let key = KvStorageKeys.notificationOnlyPermAlertShownKey(for: accountId)
            KvStorageService.shared.setValue(newValue, forKey: key)
        }
    }
    
    private let toastLang = ToastStrings.self
    private let tag = "BottomTabBarViewModel"
    private var cancellables: Set<AnyCancellable> = []
    private let promptDelay = 3.0 // Delay before checking Apple Health integration status and set a goal prompt
    
    init() {
        self.canShowFeedNotificationBadge = feedService.getUnreadFeedCount() > 0
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
        
        DispatchQueue.main.asyncAfter(deadline: .now() + promptDelay) {
            Task { [weak self] in
                await self?.checkAppleHealthIntegrationStatus()
                await self?.checkSetGoalCardPrompt()
                // Observe permission/state changes to decide when to show the *Permission Disabled* alert.
                self?.evaluateAndShowPermissionAlert()
                let notificationsRequired = self?.permissionsService.requiredCategories.contains(.notifications) ?? false
                if notificationsRequired {
                    await self?.pushNotificationService.setupPushNotifications()
                } else {
                    await self?.pushNotificationService.updateDeviceInfo()
                }
            }
        }
        
        // Update the app sync tab based on the app sync scale defined in the paired scale list
        scaleService.$scales
            .map { scales in
                scales.contains { $0.bathScale?.scaleType == ScaleSourceType.appsync.rawValue }
            }
            .receive(on: DispatchQueue.main)
            .assign(to: \.showAppSync, on: self)
            .store(in: &cancellables)
        
        feedService.notificationBadgeUpdated
            .receive(on: DispatchQueue.main)
            .assign(to: \.canShowFeedNotificationBadge, on: self)
            .store(in: &cancellables)

        
        // Connect GoalAlertService navigation callback
        goalAlertService.onNavigateToGoalSetting = { [weak self] in
            self?.navigateToGoalSetting()
        }
    }
    
    // MARK: - Permission Disabled Alert Helpers
    /// Evaluates whether the *Permission Disabled* alert should be shown and presents it if needed.
    private func evaluateAndShowPermissionAlert() {
        guard !hasShownPermissionAlert else { return }
        
        let required = permissionsService.requiredCategories
        // Exit early if neither Bluetooth nor Notifications are required.
        guard required.contains(.bluetooth) || required.contains(.notifications) else { return }
        
        // Current permission states
        let btSwitchState = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) ?? .ENABLED
        let btAuthState   = permissionsService.getPermissionState(.BLUETOOTH) ?? .ENABLED
        let notificationState = permissionsService.getPermissionState(.NOTIFICATION) ?? .ENABLED
        
        // Determine which categories are disabled
        let isBluetoothMissing = (required.contains(.bluetooth) && (btSwitchState != .ENABLED || btAuthState != .ENABLED))
        let isNotificationMissing = (required.contains(.notifications) && notificationState != .ENABLED)
        
        // If no permissions are missing, nothing to do.
        guard isBluetoothMissing || isNotificationMissing else { return }
        
        // Check for the *notification-only* case and whether it has already been shown.
        let isNotificationOnlyMissing = isNotificationMissing && !isBluetoothMissing
        if isNotificationOnlyMissing && notificationOnlyAlertShown {
            return // Alert already shown for this scenario – skip.
        }
        
        showPermissionDisabledAlert(isNotificationOnlyMissing: isNotificationOnlyMissing)
    }
    
    /// Presents the *Permission Disabled* alert and handles navigation when the user taps **APP PERMISSION**.
    private func showPermissionDisabledAlert(isNotificationOnlyMissing: Bool) {
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
        
        // Persist flag when alert corresponds to *notification-only* case.
        if isNotificationOnlyMissing {
            notificationOnlyAlertShown = true
        }
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
            onClose: { [weak self] in
                self?.notificationService.dismissModal()
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
              event.deviceInfo.setupType ==  .lcbt || event.deviceInfo.setupType == .btWifiR4,
              !event.deviceInfo.sku.isEmpty else {
            return false
        }
        return true
    }
    
    // MARK: - Permission Handling
    func handleCameraPermission() async -> GGPermissionState {
        // Check if camera permission is already granted
        let cameraPermissionState = permissionsService.getPermissionState(.CAMERA) ?? .ENABLED
        if cameraPermissionState == .ENABLED {
            return cameraPermissionState
        }
        return await permissionsService.handlePermission(.camera)
    }
    
    // MARK: - Set Goal Card Prompt
    /// Checks conditions to determine whether to show the *Set a Goal* card and presents it if needed.
    private func checkSetGoalCardPrompt() async {
        // Avoid duplicate prompts within the same session
        guard !hasShownSetGoalCardThisSession else { return }
        
        // Ensure we have an active account and goal settings
        guard let account = accountService.activeAccount else { return }
        
        // 1. Goal type must be nil (no goal set)
        if account.goalSettings?.goalType != nil { return }
        
        // 2. At least 3 entries must exist
        let entryCount: Int
        do {
            entryCount = try await entryService.getEntryCount()
        } catch {
            return // Could not fetch entry count – silently ignore
        }
        guard entryCount >= 3 else { return }
        
        // 3. Check KvStorage flag to see if popup already shown for this account
        let key = KvStorageKeys.setAGoalModalFlagKey(for: account.accountId)
        if (KvStorageService.shared.getValue(forKey: key) as? Bool) == true {
            return // Already shown previously
        }
        // Persist flag so it won't show again across launches
        KvStorageService.shared.setValue(true, forKey: key)
        hasShownSetGoalCardThisSession = true
        
        await MainActor.run { [weak self] in
            self?.presentSetGoalCard()
        }
    }
    
    /// Presents the Set a Goal card modal.
    private func presentSetGoalCard() {
        let modalView = SetAGoalCardView(
            onClose: { [weak notificationService] in
                notificationService?.dismissModal()
            },
            onSetGoal: { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                self.navigateToGoalSetting()
            }
        )
        let modalData = ModalData(presentedView: AnyView(modalView))
        notificationService.showModal(modalData)
    }
}
