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
    @Published var discoveredScale: DeviceSnapshot?
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
    /// Pending baby entry to assign after the user creates a new baby profile from the assign modal.
    @Published var pendingBabyAssignmentEntryId: UUID?

    // MARK: - Dependencies

    @Injector private var healthKitService: HealthKitServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector private var logger: LoggerServiceProtocol
    // New dependencies for Set Goal Card logic
    @Injector var entryService: EntryServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var deviceService: PairedDeviceServiceProtocol
    // New dependency to evaluate permission status
    @Injector private var permissionsService: PermissionsServiceProtocol
    @Injector private var pushNotificationService: PushNotificationServiceProtocol
    @Injector private var integrationService: IntegrationServiceProtocol
    @Injector var babyService: BabyServiceProtocol

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
    // MARK: - Graph Scroll Hint Tracking
    /// Keeps track if the *Scrollable Graph* discoverability hint has been shown
    /// in this app session, so a single launch can't fire it twice.
    private var hasShownGraphScrollHintThisSession: Bool = false
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
    /// Tracks the auto-save timeout task for the weight reading arrival toast.
    private var weightReadingTimeoutTask: Task<Void, Never>?
    /// Tracks the auto-save timeout task for the BPM reading arrival toast.
    private var bpmReadingTimeoutTask: Task<Void, Never>?
    // MARK: - Multiple-readings counter tracking
    // Each reading type tracks the total readings received while a card is visible.
    // The count drives the header row ("X more readings received  VIEW") shown above the title.
    // For BT batches the notification.batchCount carries the device-level total; for subsequent
    // single emissions it increments by 1 per arrival.
    // isReplacing* is set just before notificationService.showToast() so that the outgoing
    // card's onDismiss callback does not prematurely zero the counter.
    private var btWeightReadingCount: Int = 0
    private var isReplacingBtWeightCard: Bool = false
    private var wifiWeightReadingCount: Int = 0
    private var isReplacingWifiWeightCard: Bool = false
    private var btBpmReadingCount: Int = 0
    private var isReplacingBtBpmCard: Bool = false
    private var wifiBpmReadingCount: Int = 0
    private var isReplacingWifiBpmCard: Bool = false
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

        // Baby scale entries arrive already saved — show the assign/discard card.
        // Always surface baby readings even during setup (first reading from a newly paired
        // scale arrives while isSetupInProgress is still true).
        bluetoothService.newEntryReceivedPublisher
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .filter { $0.entryType == EntryType.baby.rawValue }
            .sink { [weak self] notification in
                self?.showBabyReadingArrivalCard(notification: notification)
            }
            .store(in: &cancellables)

        // Weight scale entries are held in BluetoothService pending user confirmation.
        // The entry is NOT yet saved when this fires. Suppress during setup to avoid
        // bulk-sync noise from buffered entries reconnecting.
        bluetoothService.pendingScaleEntryPublisher
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .sink { [weak self] notification in
                guard let self, !self.bluetoothService.isSetupInProgress else { return }
                self.showWeightScaleReadingArrivalCard(notification: notification)
            }
            .store(in: &cancellables)

        // BPM readings are held in BluetoothService pending user confirmation. Same
        // setup-suppression rule as weight to skip buffered readings on reconnect.
        bluetoothService.pendingBpmEntryPublisher
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .sink { [weak self] notification in
                guard let self, !self.bluetoothService.isSetupInProgress else { return }
                self.showBpmReadingArrivalCard(notification: notification)
            }
            .store(in: &cancellables)

        // Wi-Fi weight scale entries arrive already saved server-side via newEntryReceivedPublisher.
        // Unlike BT entries (pendingScaleEntryPublisher), these never need SAVE/DISCARD — only VIEW.
        // Guard: only show when (a) the entry did NOT come from a BT confirmation path
        // (BT-confirmed entries carry source == bluetoothScale or btWifiR4) and (b) the account
        // has at least one Wi-Fi-capable scale paired.
        bluetoothService.newEntryReceivedPublisher
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .filter { [weak self] notification in
                guard notification.entryType == EntryType.scale.rawValue else { return false }
                let btSources = [DeviceSourceType.bluetoothScale.rawValue, DeviceSourceType.btWifiR4.rawValue]
                guard !btSources.contains(notification.source ?? "") else { return false }
                return self?.hasWifiCapableScale ?? false
            }
            .sink { [weak self] notification in
                self?.showWifiWeightReadingCard(notification: notification)
            }
            .store(in: &cancellables)

        // Wi-Fi BPM entries follow the same already-saved pattern as Wi-Fi weight entries.
        // Guard: BT BPM entries always have a nil source (no scaleEntry); only surface the card
        // when the notification carries an explicit source (i.e. a future Wi-Fi BPM device) and
        // the account has a Wi-Fi-capable scale paired.
        bluetoothService.newEntryReceivedPublisher
            .debounce(for: .seconds(1), scheduler: DispatchQueue.main)
            .filter { [weak self] notification in
                guard notification.entryType == EntryType.bpm.rawValue else { return false }
                guard notification.source != nil else { return false }
                return self?.hasWifiCapableScale ?? false
            }
            .sink { [weak self] notification in
                self?.showWifiBpmReadingCard(notification: notification)
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
                self.checkGraphScrollHintPrompt()
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
                        await MainActor.run { self?.checkGraphScrollHintPrompt() }
                    }
                }
            }
            .store(in: &cancellables)

        // Update the app sync tab based on the app sync scale defined in the paired scale list
        deviceService.scalesPublisher
            .map { scales in
                scales.contains { $0.bathScale?.scaleType == DeviceSourceType.appsync.rawValue }
            }
            .removeDuplicates()
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
        bluetoothService.onOpenDeviceSetup = { [weak self] scale, event, isReconnect, isDuplicated in
            self?.openDeviceSetup(scale: scale, event: event, isReconnect: isReconnect, isDuplicated: isDuplicated)
        }
    }

    /// Clears goal-alert callbacks on BottomTabBarView disappearance to prevent stale closures from showing alerts during loading.
    func clearGoalAlertCallbacks() {
        goalAlertService.isOnDashboardTab = nil
        goalAlertService.onNavigateToGoalSetting = nil
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
            scale: deviceService,
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

    /// Assigns the pending baby entry (set via `pendingBabyAssignmentEntryId`) to the given baby.
    /// Called by `MyKidsAddBabyScreen` after a new baby profile is saved.
    func assignPendingEntry(to babyId: String) async {
        guard let entryId = pendingBabyAssignmentEntryId else { return }
        pendingBabyAssignmentEntryId = nil
        do {
            try await entryService.assignBabyEntry(entryId: entryId, babyId: babyId)
            logger.log(
                level: .info,
                tag: tag,
                message: "Pending baby entry assigned to new baby. entryId=\(entryId), babyId=\(babyId)"
            )
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to assign pending baby entry. entryId=\(entryId), babyId=\(babyId)",
                data: error.localizedDescription
            )
        }
    }

    /// Dismisses the “Scale Discovered” sheet.
    func dismissDiscoveredScaleSheet() {
        discoveredScale = nil
        discoveryEvent = nil
    }

    // MARK: - Connect Action from Scale Discovered Sheet

    func openDeviceSetup(scale: DeviceSnapshot, event: DeviceDiscoveryEvent?) {
        openDeviceSetup(scale: scale, event: event, isReconnect: false, isDuplicated: false)
    }

    func openDeviceSetup(scale: DeviceSnapshot, event: DeviceDiscoveryEvent?, isReconnect: Bool, isDuplicated: Bool) {
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
                    }
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed checking Apple Health integration modal state", data: error.localizedDescription)
        }
    }

    /// Presents the Apple Health Integration modal based on the given state.
    private func presentHKIntegrationModal(for state: HKIntegrationModalState) { // swiftlint:disable:this function_body_length
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

    // MARK: - Baby Reading Arrival Card

    private func babyWeightString(decigrams: Int, source: String?, isMetric: Bool) -> String {
        guard decigrams > 0 else { return "--" }
        if isMetric {
            let grad = ConversionTools.convertToDisplayWeightBase(
                decigrams: decigrams, source: source, unit: .kg, isBabyScaleEntry: true
            )
            let kg = ConversionTools.convertBabyDecigramsToKg(grad)
            return String(format: "%.3f kg", kg)
        } else {
            let grad = ConversionTools.convertToDisplayWeightBase(
                decigrams: decigrams, source: source, unit: .lbOz, isBabyScaleEntry: true
            )
            let lbsOz = ConversionTools.convertBabyDecigramsToLbsOz(grad)
            return "\(lbsOz.lbs) lbs \(String(format: "%.1f", lbsOz.oz)) oz"
        }
    }

    /// Shows a reading-arrival card when a baby scale entry arrives via Bluetooth.
    /// The entry is already persisted at this point.
    ///
    /// - Single baby: personalized title ("New Reading Received for EMMA") + DISCARD/SAVE.
    /// - Multiple babies: standard title + DON'T ASSIGN / ASSIGN → opens selection modal.
    /// - Multiple buffered readings: compact header row shows count + VIEW above the card.
    // swiftlint:disable:next function_body_length
    private func showBabyReadingArrivalCard(notification: EntryNotification) {
        let lang = DashboardStrings.self
        let isMetric = accountService.activeAccount?.weightUnit == .kg
        let weightString = babyWeightString(
            decigrams: notification.babyWeight ?? 0,
            source: notification.babySource,
            isMetric: isMetric
        )
        let relativeTime = DateTimeTools.getArrivalRelativeTime(fromISOString: notification.entryTimestamp)
            ?? DashboardStrings.justNow
        let message = "\(weightString) · \(relativeTime)"
        let entryId = notification.id

        // No baby profile exists — surface an "ADD A BABY" CTA (MOB-425).
        guard !babyService.currentBabies.isEmpty else {
            showBabyReadingNoProfileCard(entryId: entryId, weightString: weightString, relativeTime: relativeTime)
            return
        }

        var didUserAct = false
        // Extract primitives before any Task boundary — Baby is non-Sendable.
        let activeBabyId = babyService.currentBabies.first?.id
        let babyItems: [AssignBabyModalView.BabyItem] = babyService.currentBabies.map {
            AssignBabyModalView.BabyItem(id: $0.id, name: $0.name, birthday: $0.birthday)
        }

        let autoAssign: () -> Void = { [weak self] in
            guard let self, let activeBabyId else { return }
            Task { @MainActor [weak self] in
                guard let self else { return }
                do {
                    try await self.entryService.assignBabyEntry(entryId: entryId, babyId: activeBabyId)
                    self.logger.log(level: .info, tag: self.tag, message: "Baby reading auto-assigned to babyId=\(activeBabyId), entryId=\(entryId)")
                } catch {
                    self.logger.log(level: .error, tag: self.tag, message: "Failed to auto-assign baby reading. entryId=\(entryId)", data: error.localizedDescription)
                }
            }
        }

        let onDismiss: () -> Void = { [weak self] in
            guard let self, !didUserAct else { return }
            autoAssign()
        }

        let toast: ToastModel
        if babyItems.count == 1, let singleBabyId = activeBabyId {
            // Single baby: personalized title + SAVE/DISCARD (no selection modal).
            let singleBabyName = babyItems[0].name
            toast = ToastModel(
                title: lang.babyReadingArrivalTitleForSingleBaby(singleBabyName),
                message: "",
                btnTextView: AnyView(
                    WeightScaleReadingArrivalCTAView(
                        weightWithUnit: weightString,
                        timestamp: relativeTime,
                        onSave: { [weak self] in
                            didUserAct = true
                            guard let self else { return }
                            self.notificationService.dismissToast()
                            Task { @MainActor [weak self] in
                                guard let self else { return }
                                do {
                                    try await self.entryService.assignBabyEntry(entryId: entryId, babyId: singleBabyId)
                                    self.logger.log(level: .info, tag: self.tag, message: "Baby reading saved (single baby). babyId=\(singleBabyId), entryId=\(entryId)")
                                    self.showAssignedBabyToast(
                                        babyName: singleBabyName,
                                        entryId: entryId,
                                        weightString: weightString,
                                        weightMessage: message,
                                        babyItems: babyItems
                                    )
                                } catch {
                                    self.logger.log(level: .error, tag: self.tag, message: "Failed to save baby reading (single baby). entryId=\(entryId)", data: error.localizedDescription)
                                }
                            }
                        },
                        onDiscard: { [weak self] in
                            didUserAct = true
                            guard let self else { return }
                            self.notificationService.dismissToast()
                            self.discardBabyReading(entryId: entryId)
                        }
                    )
                ),
                duration: 8.0,
                onDismiss: onDismiss
            )
        } else {
            // Multiple babies: DON'T ASSIGN / ASSIGN → opens modal.
            toast = ToastModel(
                title: lang.babyReadingArrivalTitle,
                message: "",
                btnTextView: AnyView(
                    BabyReadingArrivalCTAView(
                        weightString: weightString,
                        timestamp: relativeTime,
                        onAssign: { [weak self] in
                            didUserAct = true
                            guard let self else { return }
                            self.notificationService.dismissToast()
                            self.showAssignBabyModal(
                                entryId: entryId,
                                weightString: weightString,
                                weightMessage: message,
                                babyItems: babyItems
                            )
                        },
                        onDiscard: { [weak self] in
                            didUserAct = true
                            guard let self else { return }
                            self.notificationService.dismissToast()
                            self.discardBabyReading(entryId: entryId)
                        }
                    )
                ),
                duration: 8.0,
                onDismiss: onDismiss
            )
        }

        logger.log(level: .info, tag: tag, message: "Showing baby reading arrival card. entryId=\(entryId)")
        notificationService.showToast(toast)
    }

    /// Shown when a baby scale reading arrives but no baby profile exists (MOB-425).
    /// Offers an "ADD A BABY" CTA that deep-links to the add-a-baby flow in Settings,
    /// keeping the reading so it can be saved against the new baby. If the user discards
    /// the card — or lets it time out without adding a baby — the reading is discarded and
    /// won't appear in History (per the MOB-425 design).
    private func showBabyReadingNoProfileCard(entryId: UUID, weightString: String, relativeTime: String) {
        var didUserAct = false

        let toast = ToastModel(
            title: DashboardStrings.babyReadingArrivalTitle,
            message: "",
            btnTextView: AnyView(
                BabyReadingNoProfileCTAView(
                    weightString: weightString,
                    timestamp: relativeTime,
                    onAddBaby: { [weak self] in
                        didUserAct = true
                        guard let self else { return }
                        self.notificationService.dismissToast()
                        self.pendingBabyAssignmentEntryId = entryId
                        self.navigateToSettings(route: .addBaby)
                        self.logger.log(
                            level: .info,
                            tag: self.tag,
                            message: "Baby reading no-profile CTA tapped; routing to add-a-baby. entryId=\(entryId)"
                        )
                    },
                    onDiscard: { [weak self] in
                        didUserAct = true
                        guard let self else { return }
                        self.notificationService.dismissToast()
                        self.discardBabyReading(entryId: entryId)
                    }
                )
            ),
            duration: 8.0,
            onDismiss: { [weak self] in
                // No baby was added — discard the reading so it doesn't linger orphaned.
                guard !didUserAct else { return }
                self?.discardBabyReading(entryId: entryId)
            }
        )

        logger.log(
            level: .info,
            tag: tag,
            message: "Showing baby reading no-profile card. entryId=\(entryId)"
        )
        notificationService.showToast(toast)
    }

    /// Deletes a baby scale reading entry. Used when the user discards a reading, or when
    /// the no-profile card times out without a baby being added (MOB-425).
    private func discardBabyReading(entryId: UUID) {
        Task { [weak self] in
            guard let self else { return }
            do {
                try await self.entryService.deleteEntry(entryId: entryId)
                self.logger.log(level: .info, tag: self.tag, message: "Baby reading discarded. entryId=\(entryId)")
            } catch {
                self.logger.log(
                    level: .error,
                    tag: self.tag,
                    message: "Failed to discard baby reading. entryId=\(entryId)",
                    data: error.localizedDescription
                )
                self.notificationService.showToast(
                    ToastModel(message: "Failed to remove reading. Please try again.")
                )
            }
        }
    }

    /// Presents the baby-selection modal so the user can choose which baby to assign the entry to.
    private func showAssignBabyModal(
        entryId: UUID,
        weightString: String,
        weightMessage: String,
        babyItems: [AssignBabyModalView.BabyItem]
    ) {
        let modalView = AssignBabyModalView(
            babies: babyItems,
            weightMessage: weightMessage,
            onAssign: { [weak self] selectedBabyId in
                guard let self else { return }
                self.notificationService.dismissModal()
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    do {
                        try await self.entryService.assignBabyEntry(entryId: entryId, babyId: selectedBabyId)
                        let babyName = babyItems.first(where: { $0.id == selectedBabyId })?.name ?? ""
                        self.logger.log(
                            level: .info,
                            tag: self.tag,
                            message: "Baby reading assigned to babyId=\(selectedBabyId), entryId=\(entryId)"
                        )
                        self.showAssignedBabyToast(
                            babyName: babyName,
                            entryId: entryId,
                            weightString: weightString,
                            weightMessage: weightMessage,
                            babyItems: babyItems
                        )
                    } catch {
                        self.logger.log(
                            level: .error,
                            tag: self.tag,
                            message: "Failed to assign baby reading. entryId=\(entryId)",
                            data: error.localizedDescription
                        )
                    }
                }
            },
            onDontAssign: { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                Task { [weak self] in
                    guard let self else { return }
                    do {
                        try await self.entryService.deleteEntry(entryId: entryId)
                        self.logger.log(
                            level: .info,
                            tag: self.tag,
                            message: "Baby reading discarded from assign modal. entryId=\(entryId)"
                        )
                    } catch {
                        self.logger.log(
                            level: .error,
                            tag: self.tag,
                            message: "Failed to discard baby reading from assign modal. entryId=\(entryId)",
                            data: error.localizedDescription
                        )
                    }
                }
            },
            onClose: { [weak self] in
                self?.notificationService.dismissModal()
            },
            onAddNewBaby: { [weak self] in
                guard let self else { return }
                self.notificationService.dismissModal()
                self.pendingBabyAssignmentEntryId = entryId
                self.navigateToSettings(route: .addBaby)
                self.logger.log(
                    level: .info,
                    tag: self.tag,
                    message: "Navigating to Add Baby to create new profile from assign modal. entryId=\(entryId)"
                )
            }
        )
        notificationService.showModal(ModalData(presentedView: AnyView(modalView)))
    }

    /// Shows a confirmation toast after a baby reading has been successfully assigned.
    /// Includes a REASSIGN button to re-open the baby selection modal.
    private func showAssignedBabyToast(
        babyName: String,
        entryId: UUID,
        weightString: String,
        weightMessage: String,
        babyItems: [AssignBabyModalView.BabyItem]
    ) {
        let toast = ToastModel(
            title: nil,
            message: "",
            btnTextView: AnyView(
                BabyReadingAssignedToastView(
                    weightString: weightString,
                    babyName: babyName,
                    onReassign: { [weak self] in
                        self?.notificationService.dismissToast()
                        self?.showAssignBabyModal(
                            entryId: entryId,
                            weightString: weightString,
                            weightMessage: weightMessage,
                            babyItems: babyItems
                        )
                    }
                )
            ),
            duration: 4.0,
            onDismiss: nil
        )

        logger.log(level: .info, tag: tag, message: "Showing assigned baby toast. babyName=\(babyName)")
        notificationService.showToast(toast)
    }

    private func weightDisplayString(stored: Int, isMetric: Bool) -> String {
        guard stored > 0 else { return "--" }
        let display = ConversionTools.convertStoredToDisplay(Double(stored), isMetric: isMetric)
        return isMetric
            ? String(format: "%.1f kg", display)
            : String(format: "%.1f lbs", display)
    }

    /// Shows a reading-arrival card when a BT weight scale entry arrives.
    /// The entry has NOT been saved yet. SAVE confirms it; DISCARD drops it.
    /// Auto-saves on timeout. When multiple readings arrive in a session a compact
    /// counter header row ("X more readings received  VIEW") is shown above the card.
    private func showWeightScaleReadingArrivalCard(notification: EntryNotification) { // swiftlint:disable:this function_body_length
        let lang = DashboardStrings.self
        let isMetric = accountService.activeAccount?.weightUnit == .kg
        let weightString = weightDisplayString(stored: notification.weight ?? 0, isMetric: isMetric)
        let relativeTime = DateTimeTools.getArrivalRelativeTime(fromISOString: notification.entryTimestamp)
            ?? DashboardStrings.justNow
        let message = "\(weightString) - \(relativeTime)"
        let toastDuration = 8.0

        weightReadingTimeoutTask?.cancel()

        btWeightReadingCount += notification.batchCount
        let count = btWeightReadingCount

        let onViewHeader: () -> Void = { [weak self] in
            guard let self else { return }
            self.btWeightReadingCount = 0
            self.weightReadingTimeoutTask?.cancel()
            self.notificationService.dismissToast()
            self.selectTab(.history)
            Task { [weak self] in
                try? await self?.bluetoothService.confirmPendingScaleEntry()
            }
        }

        let headerView: AnyView? = count > 1
            ? AnyView(MultipleReadingsToastView(count: count - 1, onView: onViewHeader))
            : nil

        let onDismiss: () -> Void = { [weak self] in
            guard let self, !self.isReplacingBtWeightCard else {
                self?.isReplacingBtWeightCard = false
                return
            }
            self.btWeightReadingCount = 0
        }

        let toast = ToastModel(
            title: lang.weightReadingArrivalTitle,
            message: "",
            headerView: headerView,
            btnTextView: AnyView(
                WeightScaleReadingArrivalCTAView(
                    weightWithUnit: weightString,
                    timestamp: relativeTime,
                    onSave: { [weak self] in
                        guard let self else { return }
                        self.btWeightReadingCount = 0
                        self.weightReadingTimeoutTask?.cancel()
                        self.notificationService.dismissToast()
                        Task { [weak self] in
                            guard let self else { return }
                            do {
                                try await self.bluetoothService.confirmPendingScaleEntry()
                            } catch {
                                self.logger.log(level: .error, tag: self.tag, message: "Failed to save weight reading.", data: error.localizedDescription)
                            }
                        }
                    },
                    onDiscard: { [weak self] in
                        guard let self else { return }
                        self.btWeightReadingCount = 0
                        self.weightReadingTimeoutTask?.cancel()
                        self.bluetoothService.discardPendingScaleEntry()
                        self.notificationService.dismissToast()
                        self.logger.log(level: .info, tag: self.tag, message: "Weight reading discarded.")
                    }
                )
            ),
            duration: toastDuration,
            onDismiss: onDismiss
        )

        // Auto-save after the toast duration if the user didn't act.
        weightReadingTimeoutTask = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: UInt64(toastDuration * 1_000_000_000))
            } catch {
                return
            }
            guard let self else { return }
            self.btWeightReadingCount = 0
            do {
                try await self.bluetoothService.confirmPendingScaleEntry()
            } catch {
                self.logger.log(level: .error, tag: self.tag, message: "Failed to auto-save weight reading on timeout.", data: error.localizedDescription)
            }
        }

        if count > 1 { isReplacingBtWeightCard = true }
        logger.log(level: .info, tag: tag, message: "Showing weight reading arrival card. count=\(count), batchCount=\(notification.batchCount)")
        notificationService.showToast(toast)
    }

    /// Shows a reading-arrival card when a BT BPM reading arrives.
    /// The entry has NOT been saved yet. SAVE confirms it; DISCARD drops it.
    /// Auto-saves on timeout. Multiple readings in a session add a counter header row.
    private func showBpmReadingArrivalCard(notification: EntryNotification) {
        let lang = DashboardStrings.self
        let systolic = notification.systolic ?? 0
        let diastolic = notification.diastolic ?? 0
        let pulse = notification.pulse ?? 0
        let relativeTime = DateTimeTools.getArrivalRelativeTime(fromISOString: notification.entryTimestamp)
            ?? DashboardStrings.justNow
        let toastDuration = 8.0

        bpmReadingTimeoutTask?.cancel()

        btBpmReadingCount += notification.batchCount
        let count = btBpmReadingCount

        let onViewHeader: () -> Void = { [weak self] in
            guard let self else { return }
            self.btBpmReadingCount = 0
            self.bpmReadingTimeoutTask?.cancel()
            self.notificationService.dismissToast()
            self.selectTab(.history)
            Task { [weak self] in
                try? await self?.bluetoothService.confirmPendingBpmEntry()
            }
        }

        let headerView: AnyView? = count > 1
            ? AnyView(MultipleReadingsToastView(count: count - 1, onView: onViewHeader))
            : nil

        let onDismiss: () -> Void = { [weak self] in
            guard let self, !self.isReplacingBtBpmCard else {
                self?.isReplacingBtBpmCard = false
                return
            }
            self.btBpmReadingCount = 0
        }

        let toast = ToastModel(
            title: lang.bpmReadingArrivalTitle,
            message: "",
            headerView: headerView,
            btnTextView: AnyView(
                BpmReadingArrivalCTAView(
                    systolic: systolic,
                    diastolic: diastolic,
                    pulse: pulse,
                    timestamp: relativeTime,
                    onSave: { [weak self] in
                        guard let self else { return }
                        self.btBpmReadingCount = 0
                        self.bpmReadingTimeoutTask?.cancel()
                        self.notificationService.dismissToast()
                        Task { [weak self] in
                            guard let self else { return }
                            do {
                                try await self.bluetoothService.confirmPendingBpmEntry()
                            } catch {
                                self.logger.log(level: .error, tag: self.tag, message: "Failed to save BPM reading.", data: error.localizedDescription)
                            }
                        }
                    },
                    onDiscard: { [weak self] in
                        guard let self else { return }
                        self.btBpmReadingCount = 0
                        self.bpmReadingTimeoutTask?.cancel()
                        self.bluetoothService.discardPendingBpmEntry()
                        self.notificationService.dismissToast()
                        self.logger.log(level: .info, tag: self.tag, message: "BPM reading discarded.")
                    }
                )
            ),
            duration: toastDuration,
            onDismiss: onDismiss
        )

        // Auto-save after the toast duration if the user didn't act.
        bpmReadingTimeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(toastDuration * 1_000_000_000))
            guard !Task.isCancelled, let self else { return }
            self.btBpmReadingCount = 0
            do {
                try await self.bluetoothService.confirmPendingBpmEntry()
            } catch {
                self.logger.log(level: .error, tag: self.tag, message: "Failed to auto-save BPM reading on timeout.", data: error.localizedDescription)
            }
        }

        if count > 1 { isReplacingBtBpmCard = true }
        logger.log(level: .info, tag: tag, message: "Showing BPM reading arrival card. count=\(count), batchCount=\(notification.batchCount)")
        notificationService.showToast(toast)
    }

    // MARK: - Wi-Fi Reading Arrival Cards

    /// Shows a VIEW-only arrival card when a Wi-Fi weight entry is received.
    /// Wi-Fi entries are already saved server-side — no SAVE/DISCARD needed.
    /// When multiple readings arrive in a session, a compact counter header row
    /// ("X more readings received  VIEW") appears above the latest reading.
    private func showWifiWeightReadingCard(notification: EntryNotification) {
        let isMetric = accountService.activeAccount?.weightUnit == .kg
        let weightString = weightDisplayString(stored: notification.weight ?? 0, isMetric: isMetric)
        let relativeTime = DateTimeTools.getArrivalRelativeTime(fromISOString: notification.entryTimestamp)
            ?? DashboardStrings.justNow

        wifiWeightReadingCount += 1
        let count = wifiWeightReadingCount

        let onView: () -> Void = { [weak self] in
            guard let self else { return }
            self.wifiWeightReadingCount = 0
            self.notificationService.dismissToast()
            self.selectTab(.history)
            self.logger.log(level: .info, tag: self.tag, message: "Wi-Fi weight reading VIEW tapped; navigating to History.")
        }

        let onDismiss: () -> Void = { [weak self] in
            guard let self, !self.isReplacingWifiWeightCard else {
                self?.isReplacingWifiWeightCard = false
                return
            }
            self.wifiWeightReadingCount = 0
        }

        let headerView: AnyView? = count > 1
            ? AnyView(MultipleReadingsToastView(count: count - 1, onView: onView))
            : nil

        let toast = ToastModel(
            title: DashboardStrings.wifiReadingArrivalTitle,
            message: "\(weightString) - \(relativeTime)",
            headerView: headerView,
            btnTextView: AnyView(ReadingArrivalViewCTAView(onView: onView)),
            duration: 8.0,
            onDismiss: onDismiss
        )

        if count > 1 { isReplacingWifiWeightCard = true }
        logger.log(level: .info, tag: tag, message: "Showing Wi-Fi weight reading card. count=\(count)")
        notificationService.showToast(toast)
    }

    /// Shows a VIEW-only arrival card when a Wi-Fi BPM entry is received.
    /// Wi-Fi BPM entries are already saved server-side — no SAVE/DISCARD needed.
    /// Multiple readings add a counter header row above the latest reading.
    private func showWifiBpmReadingCard(notification: EntryNotification) {
        let systolic = notification.systolic ?? 0
        let diastolic = notification.diastolic ?? 0
        let pulse = notification.pulse ?? 0
        let relativeTime = DateTimeTools.getArrivalRelativeTime(fromISOString: notification.entryTimestamp)
            ?? DashboardStrings.justNow

        wifiBpmReadingCount += 1
        let count = wifiBpmReadingCount

        let onView: () -> Void = { [weak self] in
            guard let self else { return }
            self.wifiBpmReadingCount = 0
            self.notificationService.dismissToast()
            self.selectTab(.history)
            self.logger.log(level: .info, tag: self.tag, message: "Wi-Fi BPM reading VIEW tapped; navigating to History.")
        }

        let onDismiss: () -> Void = { [weak self] in
            guard let self, !self.isReplacingWifiBpmCard else {
                self?.isReplacingWifiBpmCard = false
                return
            }
            self.wifiBpmReadingCount = 0
        }

        let headerView: AnyView? = count > 1
            ? AnyView(MultipleReadingsToastView(count: count - 1, onView: onView))
            : nil

        // BPM values shown in message; btnTextView holds the single VIEW button.
        let bpmMessage = "\(systolic)/\(diastolic) \(DashboardStrings.bpmReadingArrivalMmhg) \(pulse) \(DashboardStrings.bpmReadingArrivalPulse) - \(relativeTime)"

        let toast = ToastModel(
            title: DashboardStrings.wifiReadingArrivalTitle,
            message: bpmMessage,
            headerView: headerView,
            btnTextView: AnyView(ReadingArrivalViewCTAView(onView: onView)),
            duration: 8.0,
            onDismiss: onDismiss
        )

        if count > 1 { isReplacingWifiBpmCard = true }
        logger.log(level: .info, tag: tag, message: "Showing Wi-Fi BPM reading card. count=\(count)")
        notificationService.showToast(toast)
    }

    // MARK: - Wi-Fi Capability Helpers

    /// Returns true when at least one paired scale supports Wi-Fi readings
    /// (pure Wi-Fi scales or BT+Wi-Fi R4 hybrids). Used to gate the
    /// "New Reading saved to your log" card so it never fires for BT-only accounts.
    private var hasWifiCapableScale: Bool {
        deviceService.scales.contains { scale in
            let type = scale.bathScale?.scaleType
            return type == DeviceModelType.wifi.rawValue || type == DeviceModelType.bluetoothR4.rawValue
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

        if account.goalType != nil { return }

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

    // MARK: - Graph Scroll Hint Prompt (MA-3925 / MA-3984)

    /// Shows the *Scrollable Graph* first-time discoverability modal once per
    /// account. Skips when an account is missing, the user is off the Dashboard
    /// tab, or the per-user flag is already set in local storage.
    private func checkGraphScrollHintPrompt() {
        guard !hasShownGraphScrollHintThisSession else { return }
        guard selectedTab == .dash else { return }
        guard let account = accountService.activeAccount else { return }

        let key = KvStorageKeys.graphScrollHintViewedKey(for: account.accountId)
        if (KvStorageService.shared.getValue(forKey: key) as? Bool) == true {
            return
        }

        KvStorageService.shared.setValue(true, forKey: key)
        hasShownGraphScrollHintThisSession = true

        presentGraphScrollHintModal()
    }

    private func presentGraphScrollHintModal() {
        let modalView = GraphScrollHintModalView { [weak notificationService] in
            notificationService?.dismissModal()
        }
        logger.log(level: .info, tag: tag, message: "Presenting Scrollable-Graph hint modal")
        notificationService.showModal(ModalData(presentedView: AnyView(modalView), backdropDismiss: true))
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
