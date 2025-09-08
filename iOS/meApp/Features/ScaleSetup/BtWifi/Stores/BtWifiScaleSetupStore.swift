//
//  BtWifiScaleSetupStore.swift
//  meApp
//
//  Created by Cursor AI on 12/01/25.
//

import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the BtWifi scale setup multi-step flow.
@MainActor
final class BtWifiScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    /// Centralised permission handling service.
    @Injector private var permissionsService: PermissionsService
    /// Bluetooth service for device discovery
    @Injector private var bluetoothService: BluetoothService
    /// Account service for account operations
    @Injector private var accountService: AccountService
    /// Scale service for scale-related operations
    @Injector private var wifiScaleService: WifiScaleService
    
    @Injector private var scaleService: ScaleService
    @Injector private var pushNotificationService: PushNotificationService
    
    let networkMonitor = NetworkMonitor.shared
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: DismissAction?
    /// Discovered scale information
    private var discoveredScale: Device?
    /// Discovery event from Bluetooth service
    private var discoveryEvent: DeviceDiscoveryEvent?
    /// Cached scale token to avoid repeated API calls
    private var scaleToken: String?
    /// Cached first name from active account
    private var firstName: String?
    
    /// Exposed via a read-only computed property so views (e.g. `BtWifiScaleSetupScreen`) can react accordingly.
    private var isWifiSetupOnly: Bool = false
    /// Public accessor used by views to know whether the current flow is Wi-Fi-only (opened from Settings).
    var isWifiSetupOnlyMode: Bool { isWifiSetupOnly }
    
    /// Indicates if this is a reconnect flow
    private var isReconnect: Bool = false
    /// Public accessor for reconnect mode
    var isReconnectMode: Bool { isReconnect }
    
    /// Indicates if this is handling a duplicate user error
    private var isDuplicated: Bool = false
    /// Public accessor for duplicate user mode
    var isDuplicatedMode: Bool { isDuplicated }
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    /// Active subscription to the Bluetooth discovery publisher – only used during the *wake-up* step.
    private var deviceDiscoveryCancellable: AnyCancellable? = nil
    /// Active subscription to the network form changes
    private var networkFormCancellable: AnyCancellable? = nil
    /// Active subscription to new entry events during measurement
    private var newEntrySubscription: AnyCancellable? = nil
    /// Active subscription to live measurement data during step-on phase
    private var liveMeasurementSubscription: AnyCancellable? = nil
    /// Task handling measurement timeout
    private var measurementTimeoutTask: Task<Void, Never>? = nil
    
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    
    @Published private(set) var currentStep: BtWifiScaleSetupStep = .intro {
        didSet { handleStepChange() }
    }
    
    // Connection status shown on the BluetoothConnectionView.
    @Published var connectionState: ConnectionState = .loading
    
    @Published var savedScale: Device?
    
    /// Current error state for the setup flow
    @Published var scaleSetupError: BtWifiScaleSetupError = .none
    
    /// All steps in the setup flow. Exposed as read-only so views can iterate.
    @Published private(set) var steps: [BtWifiScaleSetupStep] = BtWifiScaleSetupStep.allCases
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    /// Username for duplicate user handling
    @Published var duplicateUserName: String = "" {
        didSet {
            updateNextEnabled()
        }
    }
    @Published var duplicateUserLastActiveAt: Int64? = nil
    
    /// User list from the scale
    @Published var userList: [DeviceUser] = []
    
    /// Current user found in the duplicate check
    @Published var currentUser: DeviceUser?
    
    /// List of duplicate users with the same name
    @Published var duplicateList: [DeviceUser] = []
    
    /// WiFi networks list fetched from the scale
    @Published var wifiNetworks: [WifiDetails] = []
    
    /// Connected WiFi network details
    @Published var connectedWifiNetwork: WifiDetails?
    
    /// Selected WiFi network for password entry
    @Published var selectedWifiNetwork: WifiDetails?
    
    /// Error code for the WifiConnectionView/BluetoothConnectionView
    @Published var errorCode: String? = nil
    
    /// Current customize setting being viewed
    @Published var currentCustomizeSetting: CustomizeSettings = .none
    
    /// Tracks if any changes were made in customize settings
    @Published var hasCustomizeChanges: Bool = false
    
    /// Scale mode selection (All Body Metrics or Weight Only)
    @Published var selectedScaleMode: ScaleModes = .allBodyMetrics
    
    /// Heart rate measurement setting
    @Published var isHeartRateEnabled: Bool = false
    
    /// Selected customize settings items that have been configured
    @Published var selectedCustomizeItems: Set<String> = []
    
    /// Selected scale metric keys from the Scale Metrics customization screen.
    /// Defaults to all available metrics so that, unless the user removes metrics, everything is sent to the scale.
    var selectedScaleMetrics: [String] = ScaleMetrics.defaultMetricsKeys
    
    // MARK: - Forms
    @Published var userNameForm = UserNameForm()
    @Published var networkForm = NetworkForm()
    
    
    let stepsToHideFooter: Set<BtWifiScaleSetupStep> = [
        .wakeup,
        .connectingBluetooth,
        .connectingWifi,
        .stepOn,
        .measurement,
        .updateSettings
    ]
    
    /// Task handling time-based transitions during testing.
    private var stepTimerTask: Task<Void, Never>? = nil
    
    private let tag = "BtWifiScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let alertLang = AlertStrings.self
    private let commonLang = CommonStrings.self
    private let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    private let customizeSettingsLang = BtWifiScaleSetupStrings.CustomizeSettingsStrings.self
    
    // Dashboard store used for the Dashboard Metrics customization view
    private let dashboardStore = DashboardStore()
    
    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem))
            case .permissions:
                return AnyView(PermissionListView(setupType: .btWifi))
            case .wakeup:
                return AnyView(ConnectionPromptView(
                    subtitle: scaleSetupStrings.wakeYourScaleSubtitle
                ))
            case .connectingBluetooth:
                return AnyView(
                    BluetoothConnectionView(
                        state: connectionState,
                        setupType: .btWifiR4,
                        onTryAgain: { [weak self] in
                            self?.tryAgainButtonHandler(isFromBtConnection: true)
                        },
                        onSupport: {
                            [weak self] in self?.showHelpModal()
                        }
                    )
                )
            case .gatheringNetwork:
                return AnyView(
                    Group {
                        switch scaleSetupError {
                        case .maxUserReached:
                            MaxUserListView(userList: userList).environmentObject(self)
                        case .duplicatesFound:
                            DuplicateUserView().environmentObject(self)
                        default:
                            if self.savedScale != nil {
                                ConnectionPromptView(
                                    title: ScaleSetupStrings.gatheringNetworksTitle,
                                    image: AppAssets.wifi
                                )
                            } else {
                                EmptyView()
                            }
                        }
                    }
                )
            case .availableWifiList:
                switch scaleSetupError {
                case .noNetworkFound, .wifiConnectionFailed:
                    return AnyView(WifiConnectionView(
                        state: scaleSetupError == .noNetworkFound ? .noNetworks : .failure,
                        onTryAgain: { [weak self] in self?.tryAgainButtonHandler() },
                        onSupport: {
                            [weak self] in self?.showHelpModal()
                        }
                    ))
                default:
                    return AnyView(
                        WifiSelectionView(
                            connectedWifiNetwork: connectedWifiNetwork,
                            wifiNetworks: wifiNetworks,
                            onRefresh: { [weak self] in
                                self?.tryAgainButtonHandler()
                            },
                            onNetworkSelected: { [weak self] network in
                                self?.handleNetworkSelection(network)
                            }
                        )
                    )
                }
                
            case .wifiPassword:
                if let selectedNetwork = selectedWifiNetwork {
                    return AnyView(WifiPasswordEntryView(wifiDetail: selectedNetwork).environmentObject(self))
                } else {
                    return AnyView(WifiConnectionView(
                        state: .noNetworks,
                        onTryAgain: { [weak self] in self?.tryAgainButtonHandler() },
                        onSupport: {
                            [weak self] in self?.showHelpModal()
                        }
                    ))
                }
            case .connectingWifi:
                return AnyView(WifiConnectionView(
                    state: connectionState,
                    errorCode: errorCode,
                    onTryAgain: { [weak self] in
                        self?.tryAgainButtonHandler()
                    },
                    onSupport: {
                        [weak self] in self?.showHelpModal()
                    }
                ))
            case .customizeSettings:
                return AnyView(CustomizeSettingsView().environmentObject(self))
            case .viewSettings:
                return AnyView(
                    Group {
                        switch currentCustomizeSetting {
                        case .scaleUsername:
                            DuplicateUserView(isFromCustomizeSettings: true).environmentObject(self)
                        case .scaleMode:
                            ScaleModesSelectionView(
                                selectedMode: selectedScaleMode,
                                isHeartRateEnabled: isHeartRateEnabled,
                                isR4ScaleSetup: true,
                                onBIAButtonTap: { [weak self] in
                                    self?.openBIAModel()
                                },
                                onValueChanged: { [weak self] scaleMode, heartRateEnabled in
                                    let isPulseEnabled = heartRateEnabled && scaleMode == .allBodyMetrics
                                    self?.handleScaleModeChange(scaleMode, heartRateEnabled: isPulseEnabled)
                                }
                            )
                        case .scaleMetrics:
                            // Scale metrics customization screen.
                            ScaleMetricsCustomizationView(initialEnabledKeys: selectedScaleMetrics) { [weak self] metrics in
                                self?.selectedScaleMetrics = metrics
                                // Mark that changes were made so we trigger update later.
                                self?.hasCustomizeChanges = true
                                self?.selectedCustomizeItems.insert(CustomizeSettingsItem.scaleMetrics.rawValue)
                            }
                            
                        case .dashboardMetrics:
                            ScrollView {
                                DashboardMetricsSection(
                                    store: dashboardStore,
                                    parentView: .R4ScaleSetup,
                                    openMetricInfoWithoutSelection: .constant(nil)
                                )
                                .padding(.top, .spacingSM)
                            }
                            .scrollIndicators(.hidden)
                            
                        default:
                            // For now, other settings show placeholder
                            VStack {
                                Text("Settings View: \(currentCustomizeSetting.rawValue)")
                                    .font(.body)
                                    .foregroundColor(.secondary)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                )
            case .updateSettings:
                return AnyView(
                    Group {
                        switch scaleSetupError {
                        case .updateSettingsFailed:
                            BtWifiSetupErrorStateView(
                                title: BtWifiScaleSetupStrings.BtWifiSetupErrorStateViewStrings.updateFailed,
                                errorCode: errorCode,
                                onTryAgain: { [weak self] in
                                    self?.tryAgainButtonHandler()
                                },
                                onSupport: { [weak self] in
                                    self?.showHelpModal()
                                }
                            )
                        default:
                            ConnectionPromptView(
                                title: customizeSettingsLang.updatingSettings,
                                image: AppAssets.wgLogo
                            )
                        }
                    }
                )
            case .stepOn:
                return AnyView(BtWifiSetupStepOnView())
            case .measurement:
                return AnyView(
                    Group {
                        switch scaleSetupError {
                        case .collectMeasurementFailed:
                            BtWifiSetupErrorStateView(
                                title: BtWifiScaleSetupStrings.BtWifiSetupErrorStateViewStrings.errorCollectingMeasurement,
                                errorCode: errorCode,
                                onTryAgain: { [weak self] in
                                    self?.tryAgainButtonHandler()
                                },
                                onSupport: { [weak self] in
                                    self?.showHelpModal()
                                }
                            )
                        default:
                            ConnectionPromptView(
                                title: customizeSettingsLang.collectingMeasurement,
                                image: AppAssets.wgLogo
                            )
                        }
                    }
                )
            case .scaleConnected:
                return AnyView(
                    BtWiFiFinishStepView() {
                        self.showAccuCheckInfoModal()
                    }
                )
            }
        }
    }
    
    var nextButtonText: String {
        switch currentStep {
        case .scaleConnected:
            return scaleSetupError == .collectMeasurementFailed ? CommonStrings.tryAgain : commonLang.finish
        case .gatheringNetwork:
            return scaleSetupError == .duplicatesFound ? commonLang.save : commonLang.next
        case .wifiPassword:
            return commonLang.connect
        case .viewSettings:
            return commonLang.save
        case .measurement:
            return scaleSetupError == .collectMeasurementFailed ? CommonStrings.tryAgain : commonLang.next
        default:
            return commonLang.next
        }
    }
    
    // MARK: - Lifecycle
    init() {
        // Cache the first name from active account
        self.firstName = accountService.activeAccount?.firstName ?? "User"
        
        // Observe permission updates so the footer button reacts instantly.
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
                self?.handlePermissionChange()
            }
            .store(in: &cancellables)
        
        networkMonitor.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                self?.updateNextEnabled()
                self?.handlePermissionChange()
            }
            .store(in: &cancellables)
        
        // Observe form changes to update next button state
        userNameForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)
        
        subscribeToNetworkForm()
    }
    
    // MARK: - Navigation Helpers
    func moveToNextStep() {
        let nextIndex = adjustedIndex(from: currentStepIndex + 1, direction: 1)
        guard nextIndex < steps.count else {
            dismissAction?()
            return
        }
        currentStepIndex = nextIndex
    }
    
    func moveToPreviousStep() {
        let previousIndex = adjustedIndex(from: currentStepIndex - 1, direction: -1)
        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }
    
    // MARK: - Configuration
    /// Configures the store for the given SKU, optionally injecting a previously-discovered
    /// scale and its discovery event (used when the flow originates from the *Scale Discovered* sheet).
    /// - Parameters:
    ///   - sku: The model/SKU (e.g. "\(SettingsConstants.defaultR4Sku)").
    ///   - discoveredScale: The scale object discovered by Bluetooth (optional).
    ///   - discoveryEvent: The raw discovery event emitted by `BluetoothService` (optional).
    ///   - saveScale: Previously saved scale for Wi-Fi only setup (optional).
    ///   - isReconnect: Indicates if this is a reconnect flow (optional).
    ///   - isDuplicated: Indicates if this is handling a duplicate user error (optional).
    func configure(with sku: String,
                   discoveredScale: Device? = nil,
                   discoveryEvent: DeviceDiscoveryEvent? = nil, 
                   saveScale: Device? = nil,
                   isReconnect: Bool = false,
                   isDuplicated: Bool = false) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        
        // Store reconnect and duplicate flags
        self.isReconnect = isReconnect
        self.isDuplicated = isDuplicated
        
        // Log setup state similar to Angular version
        LoggerService.shared.log(level: .info, tag: tag, message: "BtWifi setup started - Is Wifi setup: \(isWifiSetupOnly), Is Duplicated: \(isDuplicated), Is Reconnecting: \(isReconnect)")
        
        // Determine if this is a standalone Wi-Fi setup flow (opened from Settings > Wi-Fi)
        if let savedScaleParam = saveScale {
            self.savedScale = savedScaleParam
            self.scaleToken = savedScaleParam.token
            self.isWifiSetupOnly = !isReconnect
            self.bluetoothService.isSetupInProgress = true
        } else {
            self.isWifiSetupOnly = false
        }
        
        // Reset pairing/discovery state
        resetDiscoveryState()
        // Inject discovery context if provided.
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        
        // Reset error state
        self.scaleSetupError = .none
        
        // Reset customize settings state
        self.hasCustomizeChanges = false
        self.currentCustomizeSetting = .none
        self.selectedCustomizeItems = []
        
        // Set the starting step (defaults to intro, but may be permissions or connectingBluetooth for direct flow)
        let startStep: BtWifiScaleSetupStep = {
            if isReconnect && !isDuplicated {
                Task {
                    await self.getUserList()
                    self.scaleSetupError = .maxUserReached
                }
                return .gatheringNetwork
            } else if isWifiSetupOnly {
                // Directly enter the Wi-Fi flow when setting up Wi-Fi only.
                return .gatheringNetwork
            } else if discoveredScale != nil && discoveryEvent != nil {
                // When opened from sheet modal, go to connectingBluetooth if enabled, otherwise permissions
                return arePermissionsEnabled()  ? .connectingBluetooth : .permissions
            } else {
                // Normal flow starts at intro
                return .intro
            }
        }()
        if let idx = steps.firstIndex(of: startStep) {
            currentStepIndex = idx
        } else {
            currentStepIndex = 0
        }
        
        // Evaluate initial next-button state.
        updateNextEnabled()
    }
    
    
    // MARK: - Exit / Help
    private func performExitCleanup() {
        // Post notification to refresh dashboard when setup is dismissed
        NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
        
        dismissAction?()
        if savedScale == nil { disconnectDevice() }
        cancelWifi()
        self.bluetoothService.isSetupInProgress = false
    }
    
    /// Presents the standard exit-alert.
    /// - Parameters:
    ///   - onConfirm: called when user taps **Exit**
    ///   - onCancel:  called when user taps **Go Back**
    private func presentExitAlert(onConfirm: @escaping () -> Void,
                                  onCancel: @escaping () -> Void = {}) {
        let lang = AlertStrings.ExitBtWifiSetupAlert.self
        let message: String = {
            switch (isWifiSetupOnly, savedScale != nil) {
            case (true, _):  return lang.wifiExitMessage
            case (false, true):  return lang.postConnectionExitMessage
            default:  return lang.preConnectionExitMessage
            }
        }()
        
        let alert = AlertModel(
            title: lang.title,
            message: message,
            buttons: [
                AlertButtonModel(title: lang.exitButton,  type: .primary) { _ in onConfirm() },
                AlertButtonModel(title: lang.goBackButton, type: .secondary) { _ in onCancel() }
            ])
        notificationService.showAlert(alert)
    }
    
    // Called by the ✕ button.
    func handleExit() {
        guard currentStep != .scaleConnected else { performExitCleanup(); return }
        presentExitAlert(onConfirm: performExitCleanup)
    }
    
    // Used by tab-switch logic.
    func confirmExit() async -> Bool {
        if currentStep == .scaleConnected { performExitCleanup(); return true }
        
        return await withCheckedContinuation { cont in
            presentExitAlert(
                onConfirm: {
                    self.performExitCleanup()
                    cont.resume(returning: true)
                },
                onCancel: {
                    cont.resume(returning: false)
                })
        }
    }
    
    /// Shows the generic Help modal used across the app.
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(skuToNavigate: scaleItem?.sku) {
                self.notificationService.dismissModal()
            })
        ))
    }
    
    /// Checks if the footer should be shown based on the current step.
    func shouldShowFooter() -> Bool {
        // Show footer for gatheringNetwork step when there are errors that need user action
        if currentStep == .gatheringNetwork {
            return scaleSetupError == .duplicatesFound
        }
        
        // Show footer for viewSettings step
        if currentStep == .viewSettings {
            return true
        }
        
        return !stepsToHideFooter.contains(currentStep)
    }
    
    /// Checks if the back button should be disabled based on the current step.
    func shouldDisableBackButton() -> Bool {
        return currentStep == .intro || (currentStep == .gatheringNetwork && scaleSetupError == .duplicatesFound) || currentStep == .customizeSettings
    }
    
    /// Handles the next button click based on the current step.
    func handleNextButtonClick() {
        switch currentStep {
        case .gatheringNetwork:
            if scaleSetupError == .duplicatesFound {
                handleSaveDuplicateUser()
            } else {
                moveToNextStep()
            }
        case .availableWifiList:
            // If a network is already connected, proceed without asking for password
            if connectedWifiNetwork != nil {
                navigateToStep(.customizeSettings)
            } else {
                moveToNextStep()
            }
        case .wifiPassword:
            handleWifiPasswordConnect()
        case .viewSettings:
            handleViewSettingsAction()
        case .customizeSettings:
            handleCustomizeSettingsNext()
        case .scaleConnected:
            // Post notification to refresh dashboard when setup completes, right before dismissing
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
                self.dismissAction?()
            }
        default:
            moveToNextStep()
        }
    }
    
    /// Handles the next button click based on the current step.
    func handleBackButtonClick() {
        if currentStep == .wifiPassword {
            resetNetworkForm()
        } else if currentStep == .viewSettings {
            handleViewSettingsBack()
        }
        moveToPreviousStep()
    }
    
    /// Handles the restore account action from the duplicate user screen
    func handleRestoreAccount() {
        // Show confirmation alert
        let alertStrings = alertLang.ConfirmRestoreAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.backButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.restoreButton, type: .primary) { [weak self] _ in
                    Task {
                        await self?.deleteUsers()
                        // Reset to normal state and retry connection
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                            self?.scaleSetupError = .none
                            Task {
                                await self?.restartConnection()
                            }
                        }
                        self?.navigateToStep(.connectingBluetooth)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Handles the delete user action from the max user count exceeded screen
    func handleDeleteUser(_ user: DeviceUser) {
        let alertStrings = alertLang.ConfirmDeleteUserAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message(user.name),
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.deleteButton, type: .primary) { [weak self] _ in
                    Task {
                        await self?.deleteUserFromScale(user)
                        // Reset to normal state and retry connection
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                            self?.scaleSetupError = .none
                        }
                        self?.navigateToStep(.connectingBluetooth)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Handles the skip WiFi step action
    func handleSkipWifiStep() {
        let alertStrings = alertLang.SkipWifiStepAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.skipButton, type: .primary) { [weak self] _ in
                    self?.cancelWifi()
                    self?.scaleSetupError = .none
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        self?.navigateToStep(.customizeSettings)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Sets the customization page and navigates to view settings
    func setCustomizationPage(_ setting: CustomizeSettings) {
        currentCustomizeSetting = setting
        
        // Pre-populate form data based on the setting
        switch setting {
        case .scaleUsername:
            // Set the display name from saved scale or first name
            let displayName = savedScale?.r4ScalePreference?.displayName ?? firstName ?? "User"
            userNameForm.setDisplayName(displayName)
            
            // Convert DeviceUser list to ScaleUser list for form validation
            let scaleUsers = userList.map { deviceUser in
                ScaleUser(name: deviceUser.name, token: deviceUser.token)
            }
            userNameForm.updateUserList(scaleUsers)
        case .scaleMode:
            // Pre-populate scale mode settings from saved scale preferences
            if let savedScale = savedScale, let preference = savedScale.r4ScalePreference {
                selectedScaleMode = preference.shouldMeasureImpedance ? .allBodyMetrics : .weightOnly
                isHeartRateEnabled = preference.shouldMeasurePulse
            }
        case .scaleMetrics:
            // Preload currently saved display metrics so the customization screen accurately reflects existing configuration.
            if let savedScale = savedScale, let preference = savedScale.r4ScalePreference {
                selectedScaleMetrics = preference.displayMetrics
            } else {
                selectedScaleMetrics = ScaleMetrics.defaultMetricsKeys
            }
        default:
            break
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.navigateToStep(.viewSettings)
        }
    }
    
    /// Handles scale mode and heart rate changes
    func handleScaleModeChange(_ scaleMode: ScaleModes, heartRateEnabled: Bool) {
        selectedScaleMode = scaleMode
        isHeartRateEnabled = heartRateEnabled
        
        // Update the next button state
        updateNextEnabled()
    }
    
    /// Shows the showAccuCheckInfoModal.
    func showAccuCheckInfoModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(AccuCheckInfoModalView() {
                self.notificationService.dismissModal()
            })
        ))
    }
    
    /// Adds a customize settings item to the selected items set
    func addSelectedCustomizeItem(_ item: String) {
        selectedCustomizeItems.insert(item)
    }
    
    /// Checks if a customize settings item is selected
    func isCustomizeItemSelected(_ item: String) -> Bool {
        return selectedCustomizeItems.contains(item)
    }
    
    /// Handles the save action from the view settings screen
    private func handleViewSettingsAction() {
        switch currentCustomizeSetting {
        case .scaleUsername:
            // Validate the form first
            guard userNameForm.displayName.isValid else { return }
            
            // Update the scale preference with the new display name
            if let savedScale = savedScale {
                savedScale.r4ScalePreference?.displayName = userNameForm.displayName.value
            }
            self.hasCustomizeChanges = true
            break
        case .scaleMode:
            // Update the scale preference with the new scale mode settings
            if let savedScale = savedScale {
                savedScale.r4ScalePreference?.shouldMeasureImpedance = (selectedScaleMode == .allBodyMetrics)
                savedScale.r4ScalePreference?.shouldMeasurePulse = isHeartRateEnabled
            }
            self.hasCustomizeChanges = true
            break
        case .scaleMetrics:
            // Persist the user's selected display metrics into the local preference so it can be synced later.
            if let savedScale = savedScale {
                savedScale.r4ScalePreference?.displayMetrics = selectedScaleMetrics
            }
            self.hasCustomizeChanges = true
            break
        case .dashboardMetrics:
            // Mark that changes were made so the flow can treat it as updated
            // Dashboard metrics will be saved to API when Next button is clicked
            self.hasCustomizeChanges = true
            self.selectedCustomizeItems.insert(CustomizeSettingsItem.dashboardMetrics.rawValue)
            break
        default:
            break
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.currentCustomizeSetting = .none
        }
        self.moveToPreviousStep()
    }
    
    /// Handles the back action from the view settings screen
    private func handleViewSettingsBack() {
        // Reset current customize setting
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.currentCustomizeSetting = .none
        }
        
        // Don't mark changes as made when going back without saving
        // The changes flag should only be set when actually saving changes
        
        // Navigation back to customize settings will be handled by moveToPreviousStep()
    }
    
    /// Handles the next button click from the customize settings screen
    ///
    /// CUSTOMIZE SETTINGS FLOW:
    /// 1. User is on customizeSettings step
    /// 2. User clicks Next button
    /// 3. If hasCustomizeChanges is true:
    ///    - Navigate to updatingSettings step
    ///    - Show ConnectionPromptView with "Updating Settings" and wgLogo
    ///    - Call updateCustomizeSettings() to sync changes to scale
    ///    - Auto-navigate to stepOn after 2 seconds
    /// 4. If hasCustomizeChanges is false:
    ///    - Navigate directly to stepOn step
    ///
    /// NOTE: hasCustomizeChanges is set to true when:
    /// - User saves changes from viewSettings screen (via handleViewSettingsAction)
    /// - CustomizeSettingsView calls markCustomizeSettingsChanged() when settings change
    private func handleCustomizeSettingsNext() {
        if hasCustomizeChanges {
            // Show updating settings view
            navigateToStep(.updateSettings)
        } else {
            // Move directly to stepOn
            navigateToStep(.stepOn)
        }
    }
    
    /// Handles the save action from the duplicate user screen
    private func handleSaveDuplicateUser() {
        // Validate the form first
        guard userNameForm.displayName.isValid else { return }
        
        // Update duplicateUserName with the form value
        duplicateUserName = removeWhiteSpace(userNameForm.displayName.value)
        selectedCustomizeItems.insert(CustomizeSettingsItem.userName.rawValue)
        
        // Reset to normal state and retry connection
        scaleSetupError = .none
        connectionState = .loading
        navigateToStep(.connectingBluetooth)
    }
    
    /// Handles the WiFi password connect action
    private func handleWifiPasswordConnect() {
        // Validate the form first
        guard networkForm.ssid.isValid else { return }
        if !networkForm.networkHasNoPassword {
            guard networkForm.password.isValid else { return }
        }
        
        // Move to connecting WiFi step
        connectionState = .loading
        navigateToStep(.connectingWifi)
    }
    
    /// Handles the network selection from the WiFi list
    private func handleNetworkSelection(_ network: WifiDetails) {
        selectedWifiNetwork = network
        networkForm.setSSID(selectedWifiNetwork?.ssid ?? "")
        navigateToStep(.wifiPassword)
        updateNextEnabled()
    }
    
    /// Handles the pairing process when entering the *wake-up* step.
    private func pair() {
        // Start scanning for devices when entering wake-up step
        // Subscribe to discovery events (ensure we don't create multiple subscriptions).
        // Reset discovery state
        resetDiscoveryState()
        Task { bluetoothService.scanForPairing() }
        
        if deviceDiscoveryCancellable == nil {
            deviceDiscoveryCancellable = bluetoothService.deviceDiscoveredPublisher
                .receive(on: DispatchQueue.main)
                .sink { [weak self] discoveryEvent in
                    self?.handleDeviceDiscovery(discoveryEvent)
                }
        }
        
        /// Start a timer to handle the wake-up step timeout.
        stepTimerTask = Task { [weak self] in
            guard let timeoutConstants = self?.timeoutConstants.bluetoothTimeoutNs else { return }
            try? await Task.sleep(nanoseconds: UInt64(timeoutConstants))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self else { return }
                // Still on wake-up step and nothing discovered → failure
                if self.discoveredScale == nil && self.currentStep == .wakeup {
                    self.moveToNextStep()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        self.connectionState = .failure
                    }
                }
            }
        }
    }
    
    /// Invoked from the *Try Again* button of `BluetoothConnectionView` and `WifiConnectionView` failure state.
    private func tryAgainButtonHandler(isFromBtConnection: Bool = false) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            self.scaleSetupError = .none
        }
        
        // Determine which step to navigate to based on the error type
        let targetStep: BtWifiScaleSetupStep
        if isFromBtConnection {
            targetStep = .wakeup
        } else if scaleSetupError == .collectMeasurementFailed {
            targetStep = .stepOn
        } else if scaleSetupError == .updateSettingsFailed {
            targetStep = .customizeSettings
        } else {
            targetStep = .gatheringNetwork
        }
        navigateToStep(targetStep)
    }
    
    // MARK: - Step Change Handling
    private func handleStepChange() {
        switch currentStep {
        case .wakeup:
            self.pair()
        case .connectingBluetooth:
            self.connectionState = .loading
            Task {
                if discoveredScale != nil && discoveryEvent != nil {
                    await self.confirmPair()
                }
            }
        case .gatheringNetwork:
            // Only show gathering network if there's no error
            if let savedScale = self.savedScale, (scaleSetupError != .maxUserReached || scaleSetupError != .duplicatesFound) {
                // If we have a saved scale and success state, show BluetoothConnectionView for 2 seconds first
                Task {
                    await self.fetchWifiNetworks(for: savedScale)
                }
            }
        case .connectingWifi:
            self.connectionState = .loading
            if scaleSetupError == .none {
                Task {
                    await self.setupWifi()
                }
            }
        case .viewSettings:
            // Handle view settings step change
            // Make sure we have user list populated for username settings
            if currentCustomizeSetting == .scaleUsername && userList.isEmpty {
                Task {
                    await self.getUserList()
                }
            }
        case .updateSettings:
            // Simulate updating settings and move to next step
            Task {
                await self.updateCustomizeSettings()
            }
            
        case .stepOn:
            Task {
                guard let savedScale = self.savedScale else { return }
                // Subscribe to live measurement updates and proceed when weight > 0
                await bluetoothService.startLiveMeasurement(for: savedScale)
                self.liveMeasurementSubscription = self.bluetoothService.liveMeasurementPublisher
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] liveEntry in
                        guard let self else { return }
                        
                        if liveEntry.displayWeight > 0 && savedScale.broadcastIdString == liveEntry.broadcastId {
                            Task {
                                await self.bluetoothService.stopLiveMeasurement(for: savedScale)
                                self.cancelMeasurementSubscription()
                                self.scaleSetupError = .none
                                self.moveToNextStep()
                            }
                        }
                    }
                
            }
        case .measurement:
            // Cancel any existing measurement subscription and timeout
            cancelMeasurementSubscription()
            
            // Set up timeout for measurement collection
            measurementTimeoutTask = Task { [weak self] in
                guard let timeoutConstants = self?.timeoutConstants.bluetoothTimeoutNs else { return }
                try? await Task.sleep(nanoseconds: UInt64(timeoutConstants))
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    guard let self else { return }
                    // If we're still on measurement step and have an active subscription, handle timeout
                    if self.currentStep == .measurement && self.newEntrySubscription != nil {
                        self.cancelMeasurementSubscription()
                        self.scaleSetupError = .collectMeasurementFailed
                    }
                }
            }
            
            // Subscribe to new entry events
            newEntrySubscription = bluetoothService.newEntryReceivedPublisher
                .receive(on: DispatchQueue.main)
                .sink { [weak self] entry in
                    guard let self else { return }
                    // Entry received - clear timeout and move to next step
                    self.cancelMeasurementSubscription()
                    self.scaleSetupError = .none
                    self.moveToNextStep()
                }
        default:
            break
        }
    }
    
    /// Handles permission changes during the setup flow
    private func handlePermissionChange() {
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        if noNetwork && currentStep == .wakeup {
            resetDiscoveryState()
            navigateToStep(.permissions)
            return
        }
        
        guard missingPermissions else { return }
        
        switch currentStep {
        case .wakeup:
            resetDiscoveryState()
            navigateToStep(.permissions)
            
        case .gatheringNetwork:
            scaleSetupError = .wifiConnectionFailed
            navigateToStep(.availableWifiList)
            
        case .stepOn where scaleSetupError != .updateSettingsFailed:
            scaleSetupError = .collectMeasurementFailed
            moveToNextStep()
            
        default:
            break
        }
    }
    
    // MARK: - Discovery State Management
    
    /// Clears any active Bluetooth discovery subscriptions and timers and resets related state.
    private func resetDiscoveryState() {
        // Cancel active Combine subscription before releasing it.
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        
        // Nil out discovery data so subsequent runs start fresh.
        discoveredScale = nil
        discoveryEvent = nil
        
        // Cancel any in-flight timeout task.
        stepTimerTask?.cancel()
        
        // Reset error state
        scaleSetupError = .none
    }
    
    // MARK: - Scale Pairing
    /// Confirms the pairing with the discovered scale.
    private func confirmPair() async {
        guard let scale = discoveredScale, discoveryEvent != nil else {
            LoggerService.shared.log(level: .error, tag: tag, message: "confirmPair - missing discovery event or scale")
            connectionState = .failure
            return
        }
        
        // Fetch scale token if not already cached
        await fetchWifiScaleToken()
        
        guard let scaleToken = self.scaleToken else {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to obtain scale token")
            connectionState = .failure
            return
        }
        // Use cached display name, or duplicateUserName if handling duplicate user
        let displayName = !duplicateUserName.isEmpty ? duplicateUserName : (self.firstName ?? "User")
        // Call confirmSmartPair
        let pairResult = await bluetoothService.confirmSmartPair(
            device: scale,
            token: scaleToken,
            displayName: displayName,
            userNumber: nil
        )
        switch pairResult {
        case .success(let response):
            switch response {
            case .creationCompleted:
                LoggerService.shared.log(level: .info, tag: tag, message: "Creation Completed \(response)")
                await saveScale()
                connectionState = .success
                scaleSetupError = .none
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    self.navigateToStep(.gatheringNetwork)
                }
                break
            case .duplicateUserError:
                LoggerService.shared.log(level: .error, tag: tag, message: "Duplicate User Error \(response)")
                // Get user list from scale and check for duplicates
                await getUserList()
                checkDuplicateUserList()
                
                // Populate userNameForm with current user name and user list for validation
                if let firstName = self.firstName {
                    userNameForm.setDisplayName(firstName)
                }
                
                // Convert DeviceUser list to ScaleUser list for form validation
                let scaleUsers = userList.map { deviceUser in
                    ScaleUser(name: deviceUser.name, token: deviceUser.token)
                }
                userNameForm.updateUserList(scaleUsers)
                
                // Set error state and navigate to gathering network
                scaleSetupError = .duplicatesFound
                navigateToStep(.gatheringNetwork)
                break
            case .memoryFull:
                LoggerService.shared.log(level: .error, tag: tag, message: "Memory Full \(response)")
                await getUserList()
                // Set error state and navigate to gathering network
                scaleSetupError = .maxUserReached
                navigateToStep(.gatheringNetwork)
                break
            default:
                connectionState = .failure
                LoggerService.shared.log(level: .error, tag: tag, message: "Unexpected pairing response: \(response)")
                break
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to pair scale: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    /// Saves the discovered scale to persistent storage.
    private func saveScale() async {
        guard let discoveryEvent = discoveryEvent,
              let scale = discoveredScale,
              let scaleToken = self.scaleToken else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveScale - missing required data")
            return
        }
        
        do {
            let isWifiConfigured = await checkDeviceInfoAfterWifiSetup(scale: scale)
            
            // Create unique scale ID using timestamp
            let scaleID = String(DateTimeTools.getCurrentTimestampMillis())
            let displayName = !duplicateUserName.isEmpty ? duplicateUserName : (self.firstName ?? "User")
            let accountId = accountService.activeAccount?.accountId ?? ""
            
            // Get device metadata for R4 scales
            var deviceMetadata: DeviceMetaData? = nil
            let deviceInfoResult = await bluetoothService.getDeviceInfo(for: scale)
            switch deviceInfoResult {
            case .success(let deviceInfo):
                let dto = ScaleMetaDataDTO(
                    firmwareRevision: deviceInfo.firmwareRevision?.replacingOccurrences(of: "\0", with: ""),
                    hardwareRevision: deviceInfo.hardwareRevision?.replacingOccurrences(of: "\0", with: ""),
                    latestFirmwareVersion: nil,
                    manufacturerName: deviceInfo.manufacturerName?.replacingOccurrences(of: "\0", with: ""),
                    modelNumber: deviceInfo.modelNumber?.replacingOccurrences(of: "\0", with: ""),
                    serialNumber: deviceInfo.serialNumber?.replacingOccurrences(of: "\0", with: ""),
                    softwareRevision: deviceInfo.softwareRevision?.replacingOccurrences(of: "\0", with: ""),
                    systemId: deviceInfo.systemID?.replacingOccurrences(of: "\0", with: ""),
                    wifiMac: ""
                )
                deviceMetadata = DeviceMetaData(from: dto)
                LoggerService.shared.log(level: .info, tag: tag, message: "Retrieved device metadata for R4 scale")
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
            }
            
            // Get WiFi MAC address for R4 scales
            var wifiMacAddress: String? = scale.wifiMac
            let wifiMacResult = await bluetoothService.getWifiMacAddress(for: scale)
            switch wifiMacResult {
            case .success(let macAddress):
                wifiMacAddress = macAddress
                LoggerService.shared.log(level: .info, tag: tag, message: "Retrieved WiFi MAC address: \(macAddress)")
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get WiFi MAC address: \(error.localizedDescription)")
            }
            
            // Update dashboard type first
            try await accountService.updateDashboardType(type: .dashboard12)
            
            // Create the R4 scale using the proper service method
            let savedScale = try await scaleService.createR4Scale(
                scaleId: scaleID,
                accountId: accountId,
                displayName: displayName,
                token: scaleToken,
                mac: scale.mac,
                broadcastIdString: scale.broadcastIdString,
                broadcastId: scale.broadcastId,
                sku: scaleItem?.sku ?? discoveryEvent.device.sku,
                deviceName: discoveryEvent.deviceInfo.productName,
                wifiMac: wifiMacAddress,
                deviceMetadata: deviceMetadata,
                isWifiConfigured: isWifiConfigured,
                isConnected: true,
                skipDuplicateCheck: isReconnect
            )
            
            self.savedScale = savedScale
            await self.scaleService.syncAllScalesWithRemote()
            // Setup push notifications
            Task {
                await self.pushNotificationService.setupPushNotifications(isFromScaleSetup: true)
            }
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully: \(savedScale.id)")
            
            // Post notification that scale was added
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Error saving scale: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    private func saveScale2() async {
        guard let discoveryEvent = discoveryEvent,
              let scale = discoveredScale,
              let scaleToken = self.scaleToken else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveScale - missing required data")
            return
        }
        
        do {
            let isWifiConfigured = await checkDeviceInfoAfterWifiSetup(scale: scale)
            
            // Create unique scale ID using timestamp
            let scaleID = String(DateTimeTools.getCurrentTimestampMillis())
            let displayName = !duplicateUserName.isEmpty ? duplicateUserName : (self.firstName ?? "User")
            
            // Set up the scale object
            scale.id = scaleID
            scale.accountId = accountService.activeAccount?.accountId ?? ""
            scale.deviceName = discoveryEvent.deviceInfo.productName
            scale.deviceType = DeviceType.scale.rawValue
            scale.sku = scaleItem?.sku ?? discoveryEvent.device.sku
            scale.mac = scale.mac ?? ""
            scale.peripheralIdentifier = scale.mac?.replacingOccurrences(of: ":", with: "") ?? ""
            scale.userNumber = "0"
            scale.token = scaleToken
            scale.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
            scale.nickname = scale.nickname ?? "AccuCheck Verve Smart Scale"
            scale.isConnected = true
            scale.isWifiConfigured = isWifiConfigured
            
            // Set up bath scale with proper scale type
            scale.bathScale = BathScale(
                scaleType: ScaleSourceType.btWifiR4.rawValue,
                bodyComp: true
            )
            
            // Create or update R4ScalePreference
            if scale.r4ScalePreference == nil {
                scale.r4ScalePreference = R4ScalePreference(from: R4ScalePreferenceDTO(
                    scaleId: scaleID,
                    displayName: displayName,
                    displayMetrics: ScaleMetrics.defaultMetricsKeys,
                    shouldFactoryReset: false,
                    shouldMeasureImpedance: true,
                    shouldMeasurePulse: false,
                    timeFormat: "12",
                    tzOffset: DateTimeTools.getTimeZoneInMinutes(),
                    wifiFotaScheduleTime: 0,
                    updatedAt: DateTimeTools.getCurrentDatetimeIsoString(),
                    isTemporary: false
                ), scaleId: scaleID)
            }
            // Update preference properties
            scale.r4ScalePreference?.id = scaleID
            scale.r4ScalePreference?.isSynced = false
            
            // Save the scale using BluetoothService
            try await accountService.updateDashboardType(type: .dashboard12)
            
            
            let result = await bluetoothService.addNewDevice(scale, metaData: nil, isReconnect)
            switch result {
            case .success(let savedScale):
                self.savedScale = savedScale
                Task {
                    await self.pushNotificationService.setupPushNotifications(isFromScaleSetup: true)
                }
                LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully: \(savedScale.id)")
                
                // Post notification that scale was added
                NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
                
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
                connectionState = .failure
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Error saving scale: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    
    /// Fetches the WiFi scale token for setup operations.
    /// This demonstrates how to use the WiFi scale service from other services.
    private func fetchWifiScaleToken() async {
        if scaleToken != nil {
            return
        }
        
        do {
            let scaleTokenResponse = try await wifiScaleService.getScaleToken(r: "4")
            self.scaleToken = scaleTokenResponse.token
            LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi scale token: \(scaleTokenResponse.token)")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi scale token: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    /// Fetches WiFi networks from the scale and handles error cases
    private func fetchWifiNetworks(for scale: Device) async {
        do {
            // Get connected WiFi SSID first
            let connectedSSIDResult = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
            var connectedSSID: String?
            switch connectedSSIDResult {
            case .success(let ssid):
                connectedSSID = ssid.isEmpty ? nil : ssid
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get connected WiFi SSID: \(error.localizedDescription)")
                connectedSSID = nil
            }
            
            // Get WiFi networks list
            let wifiListResult = await bluetoothService.getWifiList(for: scale)
            var networks: [WifiDetails] = []
            switch wifiListResult {
            case .success(let wifiList):
                networks = wifiList
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get WiFi networks: \(error.localizedDescription)")
                throw error
            }
            
            await MainActor.run {
                self.wifiNetworks = networks
                
                // Find connected network in the list
                if let connectedSSID = connectedSSID {
                    self.connectedWifiNetwork = WifiDetails(macAddress: "", ssid: connectedSSID, rssi: 0)
                }
                
                // Check if no networks were found
                if networks.isEmpty {
                    self.scaleSetupError = .noNetworkFound
                } else {
                    self.scaleSetupError = .none
                }
                
                // Navigate to available WiFi list
                self.navigateToStep(.availableWifiList)
            }
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi networks: \(networks.count) networks found")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi networks: \(error.localizedDescription)")
            await MainActor.run {
                self.scaleSetupError = .noNetworkFound
                self.navigateToStep(.availableWifiList)
            }
        }
    }
    
    /// Sets up WiFi on the scale
    private func setupWifi() async {
        guard let scale = savedScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "setupWifi - no saved scale")
            connectionState = .failure
            return
        }
        
        let networkConfig = networkForm.getRawValue()
        
        LoggerService.shared.log(level: .info, tag: tag, message: "WiFi setup started for SSID: \(networkConfig.ssid)")
        let wifiSetupResult = await bluetoothService.setupWifi(on: scale, config: networkConfig)
        switch wifiSetupResult {
        case .success(let response):
            switch response.wifiState {
            case "GG_WIFI_STATE_CONNECTED":
                LoggerService.shared.log(level: .info, tag: tag, message: "WiFi connected for: \(networkConfig.ssid)")
                self.scaleSetupError = .none
                self.connectionState = .success
                self.errorCode = nil
                
                // Update WiFi configuration status in local database
                if let broadcastId = scale.broadcastIdString {
                    await scaleService.updateConnectedDeviceWifiStatus(broadcastId: broadcastId, isConfigured: true)
                    LoggerService.shared.log(level: .info, tag: tag, message: "Updated WiFi configuration status to true for broadcast ID: \(broadcastId)")
                }
                
                // Navigate back to root after success delay (immediate when Wi-Fi-only flow)
                let delay: TimeInterval = 2.0
                DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                    if self.isWifiSetupOnly {
                        self.dismissAction?()
                    } else {
                        self.navigateToStep(.customizeSettings)
                    }
                }
                break
            default:
                LoggerService.shared.log(level: .error, tag: tag, message: "WiFi connection failed: \(response)")
                self.connectionState = .failure
                // Extract error code from the error if available
                if let errorCode = response.errorCode {
                    self.errorCode = errorCode
                }
                break
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "WiFi connection failed: \(error.localizedDescription)")
            self.connectionState = .failure
            self.errorCode = nil
        }
        self.resetNetworkForm()
    }
    
    /// Checks device info and WiFi configuration after WiFi setup for scale SKU 0412
    private func checkDeviceInfoAfterWifiSetup(scale: Device) async -> Bool {
        var isWifiConfigured = false
        let result = await bluetoothService.getDeviceInfo(for: scale)
        switch result {
        case .success(let deviceInfo):
            isWifiConfigured = deviceInfo.isWifiConfigured ?? false// Assuming this property exists in the DeviceInfo model
            LoggerService.shared.log(level: .info, tag: tag, message: "Device info after WiFi setup - WiFi configured: \(isWifiConfigured)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info after WiFi setup: \(error)")
        }
        return isWifiConfigured
    }
    
    // MARK: - Device Discovery Handling
    private func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        // Only handle discovery during wake-up step
        guard currentStep == .wakeup else { return }
        // Only handle BtWifi scales
        guard event.deviceInfo.setupType == .btWifiR4 else { return }
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        self.discoveryEvent = event
        self.discoveredScale = event.device
        
        // Check if this is a known scale (isNew = false means it's known)
        if !event.isNew {
            showKnownScaleAlert()
        } else {
            // New scale discovered - move to next step
            moveToNextStep()
        }
    }
    
    /// Shows an alert when a known scale is discovered.
    private func showKnownScaleAlert() {
        let alertStrings = AlertStrings.knownScaleDiscoveredAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.exitButton, type: .primary) { [weak self] _ in
                    self?.dismissAction?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Deletes duplicate users from the scale
    private func deleteUsers() async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUsers - no discovered scale")
            return
        }
        
        // Delete all users in the duplicate list
        for user in duplicateList {
            scale.token = user.token
            let result = await bluetoothService.deleteDevice(scale, disconnect: false)
            switch result {
            case .success:
                LoggerService.shared.log(level: .info, tag: tag, message: "deleteUsers - deleted user: \(user.name)")
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "deleteUsers - error deleting user: \(error.localizedDescription)")
            }
        }
        
        // Reset display name to first name
        duplicateUserName = firstName ?? "User"
        
        // Reset the form with the first name
        userNameForm.reset()
        if let firstName = self.firstName {
            userNameForm.setDisplayName(firstName)
        }
    }
    
    /// Starts observing the network form changes to update the next button state.
    private func subscribeToNetworkForm() {
        // Cancel previous subscription to avoid redundant updates
        networkFormCancellable?.cancel()
        
        networkFormCancellable = networkForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
            }
    }
    
    /// Deletes a specific user from the scale
    private func deleteUserFromScale(_ user: DeviceUser) async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - no discovered scale")
            return
        }
        
        // Set the user's token to delete the correct user
        scale.token = user.token
        let result = await bluetoothService.deleteDevice(scale, disconnect: false)
        
        switch result {
        case .success:
            LoggerService.shared.log(level: .info, tag: tag, message: "deleteUserFromScale - deleted user: \(user.name)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - error deleting user: \(error.localizedDescription)")
        }
    }
    
    /// Restarts the connection process after deleting users
    private func restartConnection() async {
        // Reset duplicate user flags
        self.userList = []
        self.currentUser = nil
        self.duplicateList = []
        self.duplicateUserLastActiveAt = nil
        
        // Reset the form
        self.userNameForm.reset()
    }
    
    private func getUserList() async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - no discovered scale")
            return
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale)
        switch result {
        case .success(let users):
            // Filter out the current scale token
            self.userList = users.filter { user in
                user.token != scale.token
            }
            LoggerService.shared.log(level: .info, tag: tag, message: "getUserList - retrieved \(self.userList.count) users")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - error getting scale users: \(error.localizedDescription)")
        }
    }
    
    /// Checks for duplicate users in the user list
    private func checkDuplicateUserList() {
        self.currentUser = userList.first { user in
            user.name.lowercased() == (self.firstName?.lowercased() ?? "")
        }
        
        // Find all users with the same name as current user
        if let currentUser = self.currentUser {
            self.duplicateList = userList.filter { user in
                user.name == currentUser.name
            }
        }
        duplicateUserLastActiveAt = Int64(duplicateList.first?.lastActive ?? 0)
        LoggerService.shared.log(level: .info, tag: tag, message: "checkDuplicateUserList - found \(self.duplicateList.count) duplicate users")
    }
    
    /// Updates the customize settings on the scale
    private func updateCustomizeSettings() async {
        guard let savedScale = savedScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - no saved scale")
            await MainActor.run {
                self.scaleSetupError = .updateSettingsFailed
            }
            return
        }
        
        
        do {
            // Check which customize settings pages need to be saved
            let saveScaleMetrics = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleMetrics.rawValue)
            let saveScaleMode = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleModes.rawValue)
            let saveScaleUsername = selectedCustomizeItems.contains(CustomizeSettingsItem.userName.rawValue)
            let saveDashboardMetrics = selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue)
            
            // Save dashboard metrics to API when Next button is clicked
            if saveDashboardMetrics {
                dashboardStore.saveChanges()
                // Post notification to refresh dashboard screen when user returns to it
                NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
            }
            
            // Get current preference or create default
            let currentPreference = savedScale.r4ScalePreference ?? {
                let defaultDTO = R4ScalePreferenceDTO(
                    scaleId: savedScale.id,
                    displayName: firstName ?? "User",
                    displayMetrics: ScaleMetrics.defaultMetricsKeys,
                    shouldFactoryReset: false,
                    shouldMeasureImpedance: true,
                    shouldMeasurePulse: false,
                    timeFormat: "12",
                    tzOffset: DateTimeTools.getTimeZoneInMinutes(),
                    wifiFotaScheduleTime: 0,
                    updatedAt: DateTimeTools.getCurrentDatetimeIsoString(),
                    isTemporary: true
                )
                return R4ScalePreference(from: defaultDTO, scaleId: savedScale.id)
            }()
            
            // Build updated preference object
            let updatedPreferenceDTO = R4ScalePreferenceDTO(
                scaleId: savedScale.id,
                displayName: saveScaleUsername ? (savedScale.r4ScalePreference?.displayName ?? firstName ?? "User") : currentPreference.displayName,
                displayMetrics: saveScaleMetrics ? selectedScaleMetrics : currentPreference.displayMetrics,
                shouldFactoryReset: false,
                shouldMeasureImpedance: saveScaleMode ? (selectedScaleMode == .allBodyMetrics) : currentPreference.shouldMeasureImpedance,
                shouldMeasurePulse: saveScaleMode ? isHeartRateEnabled : currentPreference.shouldMeasurePulse,
                timeFormat: "12", // Default to 12-hour format
                tzOffset: DateTimeTools.getTimeZoneInMinutes(),
                wifiFotaScheduleTime: 0,
                updatedAt: DateTimeTools.getCurrentDatetimeIsoString(),
                isTemporary: true
            )
            
            let updatedPreference = R4ScalePreference(from: updatedPreferenceDTO, scaleId: savedScale.id)
            
            // Set up timeout task
            let timeoutTask = Task { [weak self] in
                guard let timeout = self?.timeoutConstants.updateSettingsTimeout else { return }
                
                let timeoutNs = UInt64(timeout)
                
                try? await Task.sleep(nanoseconds: timeoutNs)
                
                guard !Task.isCancelled else { return } // ← check cancellation
                
                await MainActor.run {
                    guard let self = self else { return }
                    if self.currentStep == .updateSettings {
                        self.scaleSetupError = .updateSettingsFailed
                        LoggerService.shared.log(level: .error, tag: self.tag, message: "updateCustomizeSettings - timeout occurred")
                    }
                }
            }
            
            try await scaleService.updateScalePreference(
                savedScale.id,
                updatedPreference
            )
            await scaleService.pushLocalChangesToServer()
            // Call bluetooth service to update account
            let result = await bluetoothService.updateAccount(on: savedScale, preference: updatedPreference)
            switch result {
            case .success:
                LoggerService.shared.log(level: .info, tag: tag, message: "updateCustomizeSettings - scale preference updated successfully")
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update scale preference: \(error.localizedDescription)")
            }
            
            timeoutTask.cancel()
            
            switch result {
            case .success(_):
                // Update the scale preference through the service layer to handle SwiftData relationships properly
                do {
                    try await scaleService.updateScalePreference(savedScale.id, updatedPreference)
                    
                    // Reset the changes flag after successful update
                    hasCustomizeChanges = false
                    
                    LoggerService.shared.log(level: .info, tag: tag, message: "updateCustomizeSettings - settings updated successfully: \(updatedPreference)")
                    // Clear the selected items since they're now saved
                    selectedCustomizeItems.removeAll()
                    scaleSetupError = .none
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        self.navigateToStep(.stepOn)
                    }
                } catch {
                    LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update scale preference locally: \(error.localizedDescription)")
                    await MainActor.run {
                        self.scaleSetupError = .updateSettingsFailed
                    }
                }
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update account: \(error.localizedDescription)")
                await MainActor.run {
                    self.scaleSetupError = .updateSettingsFailed
                }
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update settings: \(error.localizedDescription)")
            await MainActor.run {
                self.scaleSetupError = .updateSettingsFailed
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            self.bluetoothService.syncDevices([])
        }
    }
    
    // MARK: - Helper Methods
    
    /// Cancels measurement subscription and timeout task
    private func cancelMeasurementSubscription() {
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
        liveMeasurementSubscription?.cancel()
        liveMeasurementSubscription = nil
        measurementTimeoutTask?.cancel()
        measurementTimeoutTask = nil
    }
    
    /// NOTE: To integrate with CustomizeSettingsView:
    /// When any setting is changed in the CustomizeSettingsView,
    /// call setupStore.markCustomizeSettingsChanged() to track the change.
    /// This ensures that the next button will show the updating settings view
    /// when changes have been made, or skip directly to stepOn when no changes exist.
    /// Navigates to the specified step with an optional delay
    /// - Parameters:
    ///   - step: The step to navigate to
    ///   - delay: Optional delay in seconds before navigation (default: 0)
    private func navigateToStep(_ step: BtWifiScaleSetupStep, delay: TimeInterval = 0) {
        if let stepIndex = steps.firstIndex(of: step) {
            self.currentStepIndex = stepIndex
        }
    }
    
    /// Evaluates whether the required permissions have already been granted.
    private func arePermissionsEnabled() -> Bool {
        // For BtWifi, we need both Bluetooth and Location permissions
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED && networkMonitor.isConnected
    }
    
    /// Checks if all required permissions are available
    private func hasAllBtPermissions() -> Bool {
        return permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }
    
    /// Updates `isNextEnabled` depending on the current step and permission state.
    private func updateNextEnabled() {
        switch currentStep {
        case .permissions:
            // Evaluate individual permissions
            let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
            let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
            
            // Automatically request missing permissions
            if !bluetoothEnabled {
                Task { await permissionsService.handlePermission(.bluetooth) }
            } else if !bluetoothSwitchEnabled {
                Task { await permissionsService.handlePermission(.bluetoothSwitch) }
            }
            
            // Enable the Next button only when all permissions are granted
            isNextEnabled = bluetoothEnabled && bluetoothSwitchEnabled && networkMonitor.isConnected
        case .gatheringNetwork:
            // Enable save button only when there's a duplicate error and username is valid
            if scaleSetupError == .duplicatesFound {
                isNextEnabled = userNameForm.displayName.isValid
            } else {
                isNextEnabled = true
            }
        case .wifiPassword:
            // Enable connect button only when password is valid (unless no password required)
            if networkForm.networkHasNoPassword {
                isNextEnabled = networkForm.ssid.isValid
            } else {
                isNextEnabled = networkForm.ssid.isValid && networkForm.password.isValid
            }
        case .viewSettings:
            // Enable save button based on current customize setting
            switch currentCustomizeSetting {
            case .scaleUsername:
                isNextEnabled = userNameForm.displayName.isValid
            default:
                isNextEnabled = true
            }
        default:
            isNextEnabled = true
        }
    }
    
    /// Returns an adjusted step index by skipping the permissions page when the
    /// permission requirements are already fulfilled.
    /// - Parameters:
    ///   - index: The candidate index to navigate to.
    ///   - direction: `+1` when moving forward; `-1` when moving backwards.
    /// - Returns: A new index that omits the permissions page if it can be skipped.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .permissions,
              arePermissionsEnabled() {
            idx += direction
        }
        return idx
    }
    
    /// Opens the BIA model information modal.
    private func openBIAModel(){
        notificationService.showModal(ModalData(
            presentedView: AnyView(BIAInfoModalView(){
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    // MARK: - Cleanup Methods
    
    // Disconnects scale if it's not saved to ensure it shouldn't appears again in discovery.
    private func disconnectDevice() {
        guard let broadcastId = discoveredScale?.broadcastIdString, !broadcastId.isEmpty, savedScale == nil else { return }
        Task {
            _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId)
        }
    }
    
    // Cancels Wi-Fi to hide connecting to wifi screen on 0412 scale.
    private func cancelWifi() {
        // Cancel any in-flight Wi-Fi setup
        let scaleToCancel = discoveredScale ?? savedScale
        if let scaleToCancel = scaleToCancel {
            Task {
                await bluetoothService.cancelWifi(on: scaleToCancel)
            }
        }
    }
    
    // Resets the network form to its initial state.
    private func resetNetworkForm() {
        self.networkForm.reset()
        self.networkForm = NetworkForm()
        subscribeToNetworkForm()
    }
    
    deinit {
        // Cancel active Combine subscription before releasing it.
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        
        // Cancel network form subscription
        networkFormCancellable?.cancel()
        networkFormCancellable = nil
        
        // Cancel measurement subscriptions and timeout
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
        liveMeasurementSubscription?.cancel()
        liveMeasurementSubscription = nil
        measurementTimeoutTask?.cancel()
        measurementTimeoutTask = nil
        
        // Nil out discovery data so subsequent runs start fresh.
        discoveredScale = nil
        discoveryEvent = nil
        
        // Cancel any in-flight timeout task.
        stepTimerTask?.cancel()
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
