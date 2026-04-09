//
//  BottomTabBarViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//

import Combine

// This view model intentionally aggregates all bottom tab bar navigation logic
// and state management to maintain a single source of truth for tab state.
// Splitting would fragment navigation flow and reduce maintainability.
import Foundation
import GGBluetoothSwiftPackage
import SwiftUI

@MainActor
// swiftlint:disable:next type_body_length
class BottomTabBarViewModel: ObservableObject {
    @Injector var feedService: FeedServiceProtocol
    // Inject Bluetooth service to listen for new scale discovery events
    @Injector var bluetoothService: BluetoothServiceProtocol
    // Inject GoalAlertService to handle goal alert navigation
    @Injector var goalAlertService: GoalAlertServiceProtocol
    // Publisher-driven sheet presentation for newly discovered scales
    @Published var discoveredScale: Device?
    /// Holds the most recent Bluetooth discovery event used by the *Scale Discovered* sheet.
    @Published var discoveryEvent: DeviceDiscoveryEvent?
    @Published var selectedTab: BottomTab = .dash
    @Published var canShowFeedNotificationBadge: Bool = false
    @Published var showAppSync: Bool = false
    @Published var showTabBar: Bool = true
    /// Holds the body-composition metrics captured by AppSync when the user taps **Edit** on the confirmation card.
    /// `ManualEntryScreen` will consume this to pre-populate its form, then reset it to `nil`.
    @Published var pendingAppSyncEditMetrics: AppSyncEntryMetrics?
    /// Holds a pending navigation request to be performed by `SettingsScreen` once it appears.
    /// This is set when the user taps *Connect* in the *Add Apple Health Integration* modal.
    @Published var pendingSettingsNavigation: SettingsRoute?
    /// Tracks the source tab that initiated navigation to settings screens.
    /// Used to return to the original tab when closing settings screens.
    @Published var settingsNavigationSourceTab: BottomTab?
    @Published var setupPayload: ScaleDiscoverSheetInfo?
    /// Remembers the last selected non-AppSync tab to restore after closing scanner
    @Published private(set) var previousNonAppSyncTab: BottomTab = .dash

    // MARK: - Dependencies

    @Injector private var healthKitService: HealthKitServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector private var logger: LoggerServiceProtocol
    // New dependencies for Set Goal Card logic
    @Injector private var entryService: EntryServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var scaleService: ScaleServiceProtocol
    // New dependency to evaluate permission status
    @Injector private var permissionsService: PermissionsServiceProtocol
    @Injector private var pushNotificationService: PushNotificationServiceProtocol
    @Injector private var integrationService: IntegrationServiceProtocol

    // MARK: - Permission Disabled Alert Tracking

    /// Indicates whether the *Permission Disabled* alert has already been shown in the current app session.
    private var hasShownPermissionAlert: Bool = false
    /// Tracks whether the Apple Health integration sheet is currently presented
    private var isAppleHealthSheetPresented: Bool = false

    // MARK: - Goal Card Tracking

    /// Keeps track if the Set a Goal card has been shown in this app session.
    private var hasShownSetGoalCardThisSession: Bool = false
    /// Prevents concurrent execution of checkSetGoalCardPrompt
    private var isCheckingSetGoalCard: Bool = false
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
    /// Retains the Combine subscription for app-active notifications specifically used
    /// when we need to re-check HealthKit permissions after the user is redirected to
    /// the Apple Health app from the out-of-sync modal.
    private var hkForegroundObserver: AnyCancellable?

    init() { // swiftlint:disable:this function_body_length
        warmInjectedDependencies()
        canShowFeedNotificationBadge = feedService.getUnreadFeedCount() > 0
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

        // Subscribe to new entry events (uses EntryNotification for safe cross-actor data passing)
        bluetoothService.newEntryReceivedPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                if !self.bluetoothService.isSetupInProgress {
                    notificationService.showToast(ToastModel(title: toastLang.success, message: toastLang.entryAdded))
                }
            }
            .store(in: &cancellables)

        // Listen for Apple Health sheet presentation/dismissal notifications
        NotificationCenter.default.publisher(for: .appleHealthSheetPresented)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.isAppleHealthSheetPresented = true
            }
            .store(in: &cancellables)

        NotificationCenter.default.publisher(for: .appleHealthSheetDismissed)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.isAppleHealthSheetPresented = false
            }
            .store(in: &cancellables)

        Task { @MainActor [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: UInt64(self.promptDelay * 1_000_000_000))
            await self.checkAppleHealthIntegrationStatus()
            if self.selectedTab == .dash {
                await self.checkSetGoalCardPrompt()
            }
            self.evaluateAndShowPermissionAlert()
            let notificationsRequired = self.permissionsService.requiredCategories.contains(.notifications)
            if notificationsRequired {
                await self.pushNotificationService.setupPushNotifications()
            } else {
                await self.pushNotificationService.updateDeviceInfo()
            }
        }

        $selectedTab
            .dropFirst()
            .sink { [weak self] tab in
                if tab == .dash {
                    Task { [weak self] in
                        await self?.checkSetGoalCardPrompt()
                    }
                }
            }
            .store(in: &cancellables)

        // Update the app sync tab based on the app sync scale defined in the paired scale list
        scaleService.scalesPublisher
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

        // Observe activeAccount changes to dismiss modals when user logs out
        accountService.activeAccountPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] account in
                guard let self else { return }
                if account == nil {
                    // User logged out - dismiss all modals
                    self.notificationService.dismissAllModals()
                    self.notificationService.dismissAlert()
                    self.notificationService.dismissLoader()
                }
            }
            .store(in: &cancellables)

        // Connect GoalAlertService navigation callback
        goalAlertService.onNavigateToGoalSetting = { [weak self] in
            self?.navigateToGoalSetting()
        }

        // Set callback for GoalAlertService to check if we're on Dashboard tab
        goalAlertService.isOnDashboardTab = { [weak self] in
            return self?.selectedTab == .dash
        }

        // Check for pending goal alerts when entering bottom tab bar context
        Task { [weak self] in
            await self?.goalAlertService.checkPendingGoalAlerts()
        }

        // Connect BluetoothService scale setup navigation callback
        bluetoothService.onOpenScaleSetup = { [weak self] scale, event, isReconnect, isDuplicated in
            self?.openScaleSetup(scale: scale, event: event, isReconnect: isReconnect, isDuplicated: isDuplicated)
        }
    }

    @MainActor
    private func warmInjectedDependencies() {
        let injectedDependencies = (
            feed: feedService,
            bluetooth: bluetoothService,
            goalAlert: goalAlertService,
            healthKit: healthKitService,
            notification: notificationService,
            logger: logger,
            entry: entryService,
            account: accountService,
            scale: scaleService,
            permissions: permissionsService,
            push: pushNotificationService,
            integration: integrationService
        )
        _ = injectedDependencies
    }

    // MARK: - Permission Disabled Alert Helpers

    /// Evaluates whether the *Permission Disabled* alert should be shown and presents it if needed.
    private func evaluateAndShowPermissionAlert() {
        // Ensure active account exists before showing alert
        guard accountService.activeAccount != nil else { return }
        guard !hasShownPermissionAlert else { return }

        let required = permissionsService.requiredCategories
        // Exit early if neither Bluetooth nor Notifications are required.
        guard required.contains(.bluetooth) || required.contains(.notifications) else { return }

        // Current permission states
        let btSwitchState = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) ?? .ENABLED
        let btAuthState = permissionsService.getPermissionState(.BLUETOOTH) ?? .ENABLED
        let notificationState = permissionsService.getPermissionState(.NOTIFICATION) ?? .ENABLED

        // Determine which categories are disabled
        let isBluetoothMissing = (required.contains(.bluetooth) && (btSwitchState != .ENABLED || btAuthState != .ENABLED))
        let isNotificationMissing = (required.contains(.notifications) && notificationState != .ENABLED)

        // If no permissions are missing, nothing to do.
        guard isBluetoothMissing || isNotificationMissing else { return }

        // Check for the *notification-only* case and whether it has already been shown.
        let isNotificationOnlyMissing = isNotificationMissing && !isBluetoothMissing
        if isNotificationOnlyMissing, notificationOnlyAlertShown {
            return // Alert already shown for this scenario – skip.
        }

        showPermissionDisabledAlert(isNotificationOnlyMissing: isNotificationOnlyMissing)
    }

    /// Presents the *Permission Disabled* alert and handles navigation when the user taps **APP PERMISSION**.
    private func showPermissionDisabledAlert(isNotificationOnlyMissing: Bool) {
        // Ensure active account exists before showing alert
        guard accountService.activeAccount != nil else { return }

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
        navigateToSettings(route: .appPermissions)
    }

    // MARK: - Tab Deactivation Handling

    /// A dictionary holding async deactivation handlers for each tab. A handler should return `true` if it is safe
    /// to leave the current tab, or `false` to cancel navigation. Views are responsible for registering and removing
    /// their own handlers via `registerDeactivationHandler` / `removeDeactivationHandler`.
    private var deactivationHandlers: [BottomTab: () async -> Bool] = [:]
    /// A dictionary holding tab reselect handlers; invoked when the user taps the currently-selected tab again.
    private var reselectHandlers: [BottomTab: () -> Void] = [:]

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

    /// Registers a handler to be invoked when the user taps the currently selected tab again.
    func registerReselectHandler(for tab: BottomTab, handler: @escaping () -> Void) {
        reselectHandlers[tab] = handler
    }

    /// Handles re-selection of the current tab; if a handler is registered it will be invoked.
    func handleTabReselect(_ tab: BottomTab) {
        if let handler = reselectHandlers[tab] {
            handler()
        }
    }

    var visibleTabs: [BottomTab] {
        var tabs: [BottomTab] = [.dash, .entry, .history, .settings]
        if showAppSync {
            tabs.append(.appsync)
        }
        return tabs
    }

    func selectTab(_ tab: BottomTab) {
        if tab != .appsync {
            previousNonAppSyncTab = tab
        }

        // If switching away from settings tab, clear the source tab tracking
        if selectedTab == .settings, tab != .settings {
            clearSettingsNavigationSource()
        }

        selectedTab = tab

        // Check for Set Goal card when dashboard tab is selected
        if tab == .dash {
            Task { [weak self] in
                await self?.checkSetGoalCardPrompt()
            }
        }
    }

    /// Restores the most recent non-AppSync tab selection
    func restorePreviousTab() {
        selectTab(previousNonAppSyncTab)
    }

    /// Returns to the source tab that initiated navigation to settings screens.
    /// Clears the source tab tracking after navigation.
    func returnToSettingsSourceTab() {
        if let sourceTab = settingsNavigationSourceTab {
            settingsNavigationSourceTab = nil
            selectTab(sourceTab)
        }
    }

    /// Clears the settings navigation source tab tracking.
    func clearSettingsNavigationSource() {
        settingsNavigationSourceTab = nil
    }

    /// Navigates to a specific settings screen from any tab.
    /// - Parameters:
    ///   - route: The settings route to navigate to
    ///   - sourceTab: The tab that initiated the navigation (defaults to current selected tab)
    func navigateToSettings(route: SettingsRoute, sourceTab: BottomTab? = nil) {
        settingsNavigationSourceTab = sourceTab ?? selectedTab
        selectTab(.settings)
        pendingSettingsNavigation = route
        logger.log(
            level: .info,
            tag: tag,
            message: "Navigating to settings route. route=\(route), sourceTab=\(settingsNavigationSourceTab?.rawValue ?? "nil")"
        )
    }

    /// Navigates to the goal setting screen via the settings tab
    func navigateToGoalSetting() {
        navigateToSettings(route: .goal)
    }

    /// Dismisses the “Scale Discovered” sheet.
    func dismissDiscoveredScaleSheet() {
        discoveredScale = nil
        discoveryEvent = nil
    }

    // MARK: - Connect Action from Scale Discovered Sheet

    func openScaleSetup(scale: Device, event: DeviceDiscoveryEvent?) {
        openScaleSetup(scale: scale, event: event, isReconnect: false, isDuplicated: false)
    }

    func openScaleSetup(scale: Device, event: DeviceDiscoveryEvent?, isReconnect: Bool, isDuplicated: Bool) {
        let sku = scale.sku ?? event?.deviceInfo.sku ?? ""
        guard !sku.isEmpty, let setupType = event?.deviceInfo.setupType else { return }
        logger.log(
            level: .info,
            tag: tag,
            message: "Opening scale setup flow. sku=\(sku), setupType=\(setupType), isReconnect=\(isReconnect), isDuplicated=\(isDuplicated)"
        )
        bluetoothService.isSetupInProgress = true

        switch setupType {
        case .lcbt, .btWifiR4, .babyScale, .bpm:
            setupPayload = ScaleDiscoverSheetInfo(sku: sku, scale: scale, event: event, isReconnect: isReconnect, isDuplicated: isDuplicated)
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
        // Ensure active account exists before checking
        guard accountService.activeAccount != nil else { return }

        // First check if permissions were restored after being out of sync
        let permissionsRestored = await healthKitService.checkIfPermissionsRestoredAfterOutOfSync()
        if permissionsRestored {
            await MainActor.run { [weak self] in
                guard let self else { return }
                // Show success toast
                self.notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
            }
        }

        do {
            if let modalState = try await healthKitService.shouldShowHKIntegrationModal() {
                await MainActor.run { [weak self] in
                    // Ensure self exists before proceeding
                    guard let self else { return }
                    switch modalState {
                    case .addIntegration, .finishAdding:
                        self.presentHKIntegrationModal(for: modalState)
                    case .outOfSync:
                        self.presentHKIntegrationModal(for: .outOfSync)
                    case .updatePermissions:
                        break
                    }
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed checking Apple Health integration modal state", data: error.localizedDescription)
        }
    }

    /// Presents the Apple Health Integration modal based on the given state.
    private func presentHKIntegrationModal(for state: HKIntegrationModalState) {
        // Ensure active account exists before showing modal
        guard accountService.activeAccount != nil else { return }

        // Configure actions based on modal state
        let onPrimary: () -> Void
        let onSecondary: (() -> Void)?

        switch state {
        case .addIntegration, .finishAdding:
            onPrimary = { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                // Trigger connection flow directly instead of navigating to settings
                Task {
                    await self.handleHKConnectFlow(for: state)
                }
            }
            onSecondary = nil

        case .outOfSync:
            onPrimary = { [weak self] in
                guard let self else { return }
                // Set flag indicating we're waiting for permissions to be restored
                self.healthKitService.setWaitingForPermissionsRestored()
                self.observeForegroundForHKPermissionChanges()
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

        case .updatePermissions:
            onPrimary = { [weak self] in
                self?.notificationService.dismissModal()
            }
            onSecondary = { [weak self] in
                self?.notificationService.dismissModal()
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

        logger.log(level: .info, tag: tag, message: "Presenting Apple Health integration modal. state=\(state)")
        let modalData = ModalData(presentedView: AnyView(modalView))
        notificationService.showModal(modalData)
    }

    private func handleHKConnectFlow(for _: HKIntegrationModalState) async {
        logger.log(level: .info, tag: tag, message: "HealthKit connect flow started")
        do {
            let success = try await healthKitService.integrate(turnOn: true)

            guard success else {
                await showAlert(from: HKIntegrationHealthAccessStrings.integrationFailed)
                return
            }

            let permissionCount = healthKitService.getApprovedPermissionList().count
            let expectedCount = await healthKitService.expectedPermissionCount()
            let hasFullPermissions = permissionCount >= expectedCount
            let entryCount = (try? await entryService.getEntryCount()) ?? 0

            if entryCount > 0, hasFullPermissions {
                await showSyncAlert()
            } else {
                logger.log(level: .success, tag: tag, message: "HealthKit connect flow succeeded without historical sync")
                await MainActor.run {
                    notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
                }
            }
        } catch IntegrationError.userConflict {
            logger.log(level: .error, tag: tag, message: "HealthKit connect flow failed with user conflict")
            await showAlert(from: HKIntegrationHealthAccessStrings.userConflict)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to connect HealthKit", data: error.localizedDescription)
            await showAlert(from: HKIntegrationHealthAccessStrings.integrationFailed)
        }
    }

    private func showSyncAlert() async {
        await MainActor.run {
            let alert = AlertModel(
                title: AlertStrings.SyncWeightHistoryAlert.title,
                message: AlertStrings.SyncWeightHistoryAlert.message,
                buttons: [
                    AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.cancelButton, type: .secondary) { _ in },
                    AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.syncButton, type: .primary) { [weak self] _ in
                        Task { await self?.performSync() }
                    }
                ]
            )
            notificationService.showAlert(alert)
        }
    }

    private func performSync() async {
        logger.log(level: .info, tag: tag, message: "HealthKit historical sync started")
        await MainActor.run {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.syncing))
        }

        do {
            try await healthKitService.syncAllData()
            if let latestEntry = try await entryService.getLatestEntry() {
                // Create notification to safely pass data across actor boundaries
                let notification = EntryNotification(from: latestEntry)
                await integrationService.logHealthEntry(notification: notification)
            }
            await MainActor.run {
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(message: ToastStrings.weightHistorySynced))
            }
            logger.log(level: .success, tag: tag, message: "HealthKit historical sync succeeded")
        } catch {
            await MainActor.run {
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: ToastStrings.pleaseTryAgain))
            }
            logger.log(level: .error, tag: tag, message: "HealthKit historical sync failed", data: error.localizedDescription)
        }
    }

    private func showAlert(from content: HKIntegrationHealthAccessContent) async {
        await MainActor.run {
            let alert = AlertModel(
                title: content.title,
                message: content.description ?? "",
                buttons: [AlertButtonModel(title: CommonStrings.ok, type: .primary) { _ in }]
            )
            notificationService.showAlert(alert)
        }
    }

    // MARK: - Scale Discovery Handling

    func shouldShowDiscoveredScale(for event: DeviceDiscoveryEvent) -> Bool {
        /// Checks if the scale discovery event should trigger the "Scale Discovered" sheet.
        /// Prevents showing scale discovery when Apple Health integration sheet is already presented
        /// to avoid dismissing the Apple Health sheet unexpectedly.
        /// Also prevents showing during AppSync scanning to avoid interrupting the scanning flow.
        guard !bluetoothService.isSetupInProgress,
              bluetoothService.canShowScaleDiscoveredModal,
              !(bluetoothService.skipDevices.contains(event.device.broadcastIdString ?? "")),
              event.isNew,
              discoveredScale == nil,
              !isAppleHealthSheetPresented, // Prevent scale discovery when Apple Health sheet is shown
              selectedTab != .appsync, // Prevent scale discovery when AppSync camera is active
              event.deviceInfo.setupType == .lcbt || event.deviceInfo.setupType == .btWifiR4
              || (event.deviceInfo.setupType == .bpm && event.protocolType != .A3)
              || event.deviceInfo.setupType == .babyScale,
              !event.deviceInfo.sku.isEmpty
        else {
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

    // Checks conditions to determine whether to show the *Set a Goal* card and presents it if needed.
    private func checkSetGoalCardPrompt() async { // swiftlint:disable:this cyclomatic_complexity
        guard !hasShownSetGoalCardThisSession else { return }
        guard !isCheckingSetGoalCard else { return }
        guard selectedTab == .dash else { return }
        guard let account = accountService.activeAccount else { return }

        isCheckingSetGoalCard = true
        defer { isCheckingSetGoalCard = false }

        if account.goalSettings?.goalType != nil { return }

        let entryCount: Int
        do {
            entryCount = try await entryService.getEntryCount()
        } catch {
            return
        }
        guard entryCount >= 3 else { return }

        let key = KvStorageKeys.setAGoalModalFlagKey(for: account.accountId)
        if (KvStorageService.shared.getValue(forKey: key) as? Bool) == true {
            return
        }

        KvStorageService.shared.setValue(true, forKey: key)
        hasShownSetGoalCardThisSession = true

        await MainActor.run { [weak self] in
            guard let self else { return }
            guard self.selectedTab == .dash else { return }
            guard self.accountService.activeAccount != nil else { return }

            self.presentSetGoalCard()
        }
    }

    /// Presents the Set a Goal card modal.
    private func presentSetGoalCard() {
        guard accountService.activeAccount != nil else { return }

        Task { @MainActor [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: UInt64(self.promptDelay * 1_000_000_000))
            guard self.selectedTab == .dash else { return }
            guard self.accountService.activeAccount != nil else { return }

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
            self.logger.log(level: .info, tag: self.tag, message: "Presenting Set-a-Goal modal card from bottom tabs")
            self.notificationService.showModal(modalData)
        }
    }

    // MARK: - Apple Health Permission Observer

    /// Sets up a temporary observer that fires when the app becomes active again
    /// (i.e. user returns from the Apple Health app). At that point we re-evaluate
    /// granted permissions and, if all permissions are granted, show the success toast.
    private func observeForegroundForHKPermissionChanges() {
        // Avoid duplicating the observer.
        if hkForegroundObserver != nil { return }

        hkForegroundObserver = NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in
                guard let self else { return }
                // Check again on main actor.
                Task { @MainActor in
                    let permissionsGranted = self.healthKitService.getApprovedPermissionList().count
                    if permissionsGranted > 0 {
                        // Permissions restored - clear the waiting flag and show success toast
                        self.healthKitService.clearWaitingForPermissionsRestored()
                        self.notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
                    }
                    // Clean up observer
                    self.hkForegroundObserver?.cancel()
                    self.hkForegroundObserver = nil
                }
            }
    }
} // swiftlint:disable:this file_length
