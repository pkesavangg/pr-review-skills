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
    @Injector private var entryService: EntryService
    @Injector private var goalAlertService: GoalAlertService
    
    let networkMonitor = NetworkMonitor.shared
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?
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
    
    /// True when WiFi setup is opened from Settings (not initial scale setup)
    private var isSettingsWifiSetup: Bool {
        isWifiSetupOnly && savedScale != nil
    }
    
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
    /// Task handling stepOn screen timeout to auto-navigate to measurement screen
    private var stepOnTimeoutTask: Task<Void, Never>? = nil
    /// Task handling WiFi networks fetch - stored so we can cancel it when exiting
    private var fetchWifiNetworksTask: Task<Void, Never>? = nil
    
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            // Prevent recursive calls when reverting
            guard !isRevertingStepIndex else { return }
            
            // Don't update step if we're exiting, especially from stepOn
            if isExiting || isExitingFromStepOn {
                // Prevent any step changes when exiting
                if oldValue != currentStepIndex {
                    // Immediately revert to prevent SwiperView navigation
                    let previousIndex = oldValue
                    isRevertingStepIndex = true
                    DispatchQueue.main.async { [weak self] in
                        guard let self, (self.isExiting || self.isExitingFromStepOn) else {
                            self?.isRevertingStepIndex = false
                            return
                        }
                        self.currentStepIndex = previousIndex
                        self.isRevertingStepIndex = false
                    }
                }
                return
            }
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    
    @Published private(set) var currentStep: BtWifiScaleSetupStep = .intro {
        didSet {
            // Don't handle step change if we're exiting
            guard !isExiting else { return }
            previousStep = oldValue
            handleStepChange()
        }
    }
    
    /// Track previous step to detect navigation direction
    private var previousStep: BtWifiScaleSetupStep = .intro
    /// Flag to track if we're refreshing WiFi networks (to bypass skip logic)
    private var isRefreshingWifiNetworks: Bool = false
    
    // Flag to prevent error screens from showing during exit
    // Published so SwiftUI reacts immediately when it changes
    @Published private var isExiting: Bool = false
    
    // Flag to specifically prevent navigation from stepOn screen
    private var isExitingFromStepOn: Bool = false
    
    // Flag to prevent recursive calls in currentStepIndex didSet
    private var isRevertingStepIndex: Bool = false
    
    // Connection status shown on the BluetoothConnectionView.
    @Published var connectionState: ConnectionState = .loading {
    didSet {
        guard currentStep == .connectingBluetooth else { return }

        // Never show network errors during Bluetooth pairing
        if connectionState == .noNetworks {
            connectionState = .loading
            return
        }

        // Once pairing succeeds, don't allow network errors to override it
        if oldValue == .success,
           connectionState == .noNetworks || connectionState == .failure {
            connectionState = .success
        }
        }
    }
        
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
    
    /// Selected customize settings items that have been configured and saved
    @Published var selectedCustomizeItems: Set<String> = []
    
    /// Visited customize settings items (tracks which screens user has visited)
    @Published var visitedCustomizeItems: Set<String> = []
    
    /// Selected scale metric keys from the Scale Metrics customization screen.
    /// Defaults to all available metrics so that, unless the user removes metrics, everything is sent to the scale.
    var selectedScaleMetrics: [String] = ScaleMetrics.defaultMetricsKeys
    
    /// Snapshot of scale metrics when entering the customization screen (for change detection and cancellation)
    private var initialScaleMetricsSnapshot: [String]? = nil
    
    /// Snapshot of scale metrics when Save button was last clicked (for preserving saved changes)
    private var savedScaleMetricsSnapshot: [String]? = nil
    
    // MARK: - Forms
    @Published var userNameForm = UserNameForm()
    @Published var networkForm = NetworkForm()
    
    // MARK: - Computed Properties
    /// Check if this is being used for settings WiFi configuration
    var isSettingsContext: Bool {
        savedScale != nil && !isReconnect && !isDuplicated && isWifiSetupOnly
    }
    
    /// Check if the form is valid for WiFi password entry
    var isFormValid: Bool {
        if networkForm.networkHasNoPassword {
            return true
        } else {
            return !networkForm.password.value.isEmpty
        }
    }
    
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
    // Snapshots to detect changes and gate Save button enabling
    private var initialDisplayNameSnapshot: String?
    private var initialScaleModeSnapshot: ScaleModes?
    private var initialHeartRateEnabledSnapshot: Bool?
    private var initialDashboardMetricLabelsSnapshot: [String]? = nil
    private var initialDashboardRemovedMetricsSnapshot: Set<String>? = nil
    private var initialDashboardRemovedStreaksSnapshot: Set<String>? = nil
    private var initialDashboardStreakOrderSnapshot: [String]? = nil
    private var initialDashboardGoalCardRemovedSnapshot: Bool? = nil
    private var initialDashboardGoalCardPositionSnapshot: Int? = nil
    private var dashboardStoreCancellable: AnyCancellable? = nil
    /// Subscription to dashboard metrics updated notification to sync changes from main dashboard
    private var dashboardMetricsUpdatedCancellable: AnyCancellable? = nil
    
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
                    subtitle: scaleSetupStrings.wakeYourScaleSubtitle,
                    scaleImagePath: scaleItem.imgPath
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
                                // Show WifiConnectionView when connectionState is .noNetworks or .failure
                                if self.connectionState == .noNetworks || self.connectionState == .failure {
                                    WifiConnectionView(
                                        state: self.connectionState,
                                        setupType: .btWifiR4,
                                        isFromSettingsFlow: self.isSettingsWifiSetup,
                                        onTryAgain: { [weak self] in
                                            self?.tryAgainButtonHandler()
                                        },
                                        onSupport: { [weak self] in
                                            if self?.isSettingsWifiSetup == true {
                                                self?.showHelpModal()
                                            } else {
                                                self?.handleSkipWifiStep()
                                            }
                                        }
                                    )
                                } else {
                                    // Show loading state with ConnectionPromptView
                                    ConnectionPromptView(
                                        title: ScaleSetupStrings.gatheringNetworksTitle,
                                        image: AppAssets.wifi
                                    )
                                }
                            } else {
                                EmptyView()
                            }
                        }
                    }
                )
            case .availableWifiList:
                // Settings WiFi setup: always show list, never show error screen
                if isSettingsWifiSetup {
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
                
                // Don't show error screen if we're exiting
                if isExiting {
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
                    return AnyView(WifiPasswordEntryView(wifiDetail: selectedNetwork, isScaleSetup: true).environmentObject(self))
                } else {
                    return AnyView(WifiConnectionView(
                        state: .noNetworks,
                        isFromSettingsFlow: isSettingsWifiSetup,
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
                    isFromSettingsFlow: isSettingsWifiSetup,
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
                ScaleMetricsCustomizationView(initialEnabledKeys: selectedScaleMetrics) { [weak self] metrics, hasChanged in
                    guard let self else { return }
                    // Only update the local state, don't persist until Save is clicked
                    self.selectedScaleMetrics = metrics
                    // Re-evaluate footer button enabled state
                    self.updateNextEnabled()
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
                if isExitingFromStepOn {
                    return AnyView(EmptyView())
                }
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
        // Don't navigate if we're exiting, especially from stepOn
        guard !isExiting && !isExitingFromStepOn else { return }
        
        let nextIndex = adjustedIndex(from: currentStepIndex + 1, direction: 1)
        guard nextIndex < steps.count else {
            dismissAction?()
            return
        }
        currentStepIndex = nextIndex
    }
    
    func moveToPreviousStep() {
        // Don't navigate if we're exiting
        guard !isExiting else { return }
        
        // Settings WiFi setup: show exit alert when trying to go back from WiFi list
        if handleSettingsWifiSetupExit() {
            return
        }
        
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
                   isDuplicated: Bool = false,
                   isWifiSetupOnly: Bool
    ) {
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        // Pass original SKU to routes (not mapped), setup will save original SKU
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let resolved = SCALES.first { $0.sku == lookupSku } ?? SCALES.first
        self.scaleItem = resolved
        
        // Reset exiting flag when configuring
        isExiting = false
        
        // Store reconnect and duplicate flags
        self.isReconnect = isReconnect
        self.isDuplicated = isDuplicated
        
        // Log setup state similar to Angular version
        LoggerService.shared.log(level: .info, tag: tag, message: "BtWifi setup started - Is Wifi setup: \(isWifiSetupOnly), Is Duplicated: \(isDuplicated), Is Reconnecting: \(isReconnect)")
        
        // Set setup in progress flag immediately for ALL setup flows to prevent goal modals from appearing during setup
        self.bluetoothService.isSetupInProgress = true
        
        // Determine if this is a standalone Wi-Fi setup flow (opened from Settings > Wi-Fi)
        if let savedScaleParam = saveScale {
            self.savedScale = savedScaleParam
            self.scaleToken = savedScaleParam.token
            self.isWifiSetupOnly = !isReconnect
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
        self.visitedCustomizeItems = []
        
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
                let permissionsEnabled = arePermissionsEnabled()
                return permissionsEnabled ? .connectingBluetooth : .permissions
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
        // Ensure exiting flag is set first to prevent any navigation
        isExiting = true
        
        // Store current step and index before dismissing to prevent navigation
        let wasOnGatheringNetwork = currentStep == .gatheringNetwork
        let wasOnAvailableWifiList = currentStep == .availableWifiList
        let currentIndex = currentStepIndex
        
        // Cancel any ongoing network operations to prevent navigation after exit
        cancelNetworkScanTimeout()
        fetchWifiNetworksTask?.cancel()
        fetchWifiNetworksTask = nil
        
        // Lock the step index to current step to prevent any navigation
        // This ensures the view stays on the current screen during dismissal
        if wasOnGatheringNetwork || wasOnAvailableWifiList {
            // Ensure step index doesn't change - revert if it did
            if currentStepIndex != currentIndex {
                isRevertingStepIndex = true
                currentStepIndex = currentIndex
                isRevertingStepIndex = false
            }
        }
        
        // Post notification to refresh dashboard when setup is dismissed
        NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
        
        // Clear setup flag and dismiss the sheet FIRST
        // This ensures the sheet starts dismissing before any state changes
        bluetoothService.isSetupInProgress = false
        dismissAction?()
        
        // Delay state clearing until after sheet has started dismissing
        // This prevents state changes from happening before sheet dismissal animation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            // Clear error and connection states after sheet dismissal has started
            if wasOnGatheringNetwork || wasOnAvailableWifiList {
                self.scaleSetupError = .none
                self.connectionState = .success
            } else {
                self.scaleSetupError = .none
                self.connectionState = .success
            }
        }
        
        // Perform cleanup operations that don't affect UI
        if savedScale == nil { disconnectDevice() }
        cancelWifi()
        checkGoalModalAfterSetup()
        
        // Resume scanning and sync devices after setup exits
        Task { [weak self] in
            guard let self = self else { return }
            await self.resumeScanningAndSyncDevices()
        }
        
        // Clean up the store to break retain cycles after a delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.cleanup()
        }
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
        if currentStep == .stepOn {
            isExitingFromStepOn = true
            cancelStepOnTimeout()
            cancelMeasurementSubscription()
            if let savedScale = savedScale {
                Task.detached(priority: .userInitiated) { [weak self] in
                    guard let self else { return }
                    await self.bluetoothService.stopLiveMeasurement(for: savedScale)
                }
            }
            scaleSetupError = .none
            isExiting = true
            bluetoothService.isSetupInProgress = false
            dismissAction?()
            DispatchQueue.main.async { [weak self] in
                self?.performExitCleanup()
            }
            return
        }
        
        // Set exiting flag first to prevent any navigation during exit
        isExiting = true
        
        // Store current step to handle cleanup properly
        let wasOnGatheringNetwork = currentStep == .gatheringNetwork
        let wasOnAvailableWifiList = currentStep == .availableWifiList
        
        // Cancel any ongoing network operations to prevent navigation
        cancelNetworkScanTimeout()
        fetchWifiNetworksTask?.cancel()
        fetchWifiNetworksTask = nil
        
        // Settings WiFi setup: show exit alert when back button is tapped in WiFi list
        if handleSettingsWifiSetupExit() {
            return
        }
        
        guard currentStep != .scaleConnected else {
            performExitCleanup()
            return
        }
        presentExitAlert(onConfirm: { [weak self] in
            self?.performExitCleanup()
        })
    }
    
    // Used by tab-switch logic.
    func confirmExit() async -> Bool {
        if currentStep == .scaleConnected {
            performExitCleanup()
            return true
        }
        
        return await withCheckedContinuation { cont in
            presentExitAlert(
                onConfirm: { [weak self] in
                    self?.performExitCleanup()
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
    
    /// Handles exit from Settings WiFi setup when on available WiFi list
    /// Returns true if exit was handled (caller should return early), false otherwise
    private func handleSettingsWifiSetupExit() -> Bool {
        guard isSettingsWifiSetup && currentStep == .availableWifiList else {
            return false
        }
        
        presentExitAlert(
            onConfirm: { [weak self] in
                self?.cancelWifi()
                self?.scaleSetupError = .none
                // Dismiss sheet first, then clear states after delay
                self?.dismissAction?()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                    self?.connectionState = .success
                }
            },
            onCancel: {
                // User chose to go back - do nothing, stay on current screen
            }
        )
        return true
    }
    
    /// Shows Bluetooth turned off alert
    private func showBluetoothTurnedOffAlert() {
        let alertStrings = AlertStrings.BluetoothTurnedOffAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.cancelButton, type: .secondary) { _ in
                    // Cancel closes the alert
                },
                AlertButtonModel(title: alertStrings.turnOnButton, type: .primary) { [weak self] _ in
                    guard let self else { return }

                    if self.permissionsService.getPermissionState(.BLUETOOTH) != .ENABLED {
                        Task { await self.permissionsService.handlePermission(.bluetooth) }
                    } else if self.permissionsService.getPermissionState(.BLUETOOTH_SWITCH) != .ENABLED {
                        Task { await self.permissionsService.handlePermission(.bluetoothSwitch) }
                    }
                }
            ]
        )

        notificationService.showAlert(alert)
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
        return currentStep == .intro || (currentStep == .gatheringNetwork && scaleSetupError == .duplicatesFound) || currentStep == .customizeSettings || currentStep == .availableWifiList
    }
    
    /// Handles the next button click based on the current step.
    func handleNextButtonClick() {
        switch currentStep {
        case .intro:
            // Check permissions and network connectivity before proceeding
            let hasPermissions = hasAllBtPermissions()
            let hasNetwork = networkMonitor.isConnected
            
            if !hasPermissions || !hasNetwork {
                // Navigate to permissions screen if permissions or network are missing
                navigateToStep(.permissions)
            } else {
                // All checks passed, proceed to wakeup
                navigateToStep(.wakeup)
            }

        case .gatheringNetwork:
            if scaleSetupError == .duplicatesFound {
                handleSaveDuplicateUser()
            } else {
                moveToNextStep()
            }
        case .availableWifiList:
            // If a network is already connected, proceed without asking for password
            if connectedWifiNetwork != nil {
                // cancel Wi‑Fi flow and clear any errors before proceeding
                cancelWifi()
                scaleSetupError = .none
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    self.navigateToStep(.customizeSettings)
                }
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
            // Post notification to refresh dashboard when setup completes
            // Add a small delay to ensure connection status updates have propagated to UI
            // This prevents flicker where scales show as "not connected" briefly
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
                // Small delay to allow connection status updates to complete and propagate to UI
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    // Clear setup flag and dismiss the sheet
                    self.bluetoothService.isSetupInProgress = false
                    self.dismissAction?()
                    self.checkGoalModalAfterSetup()
                }
            }
        case .permissions:
            moveToNextStep()
        case .wakeup:
            moveToNextStep()
        case .connectingBluetooth:
            moveToNextStep()
        default:
            moveToNextStep()
        }
    }
    
    /// Handles the next button click based on the current step.
    func handleBackButtonClick() {
        // Settings WiFi setup: show exit alert when back button is tapped in WiFi list
        if handleSettingsWifiSetupExit() {
            return
        }
        
        if currentStep == .wifiPassword {
            resetNetworkForm()
        } else if currentStep == .viewSettings {
            handleViewSettingsBack()
        }
        moveToPreviousStep()
    }
    
    /// Handles the restore account action from the duplicate user screen
    func handleRestoreAccount() {
        let alertStrings = alertLang.ConfirmRestoreAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.backButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.restoreButton, type: .primary) { [weak self] _ in
                    Task {
                        await self?.performRestoreAccount()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Performs the restore account operation by finding and deleting the matching user on the scale
    private func performRestoreAccount() async {
        /// Restore requires Bluetooth (internet not required)
        guard hasAllBtPermissions() else {
            notificationService.dismissAlert()
            resetDiscoveryState()
            navigateToStep(.permissions)
            return
        }       
        guard let scale = discoveredScale else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        let accountName = getAccountNameForRestore()
		userNameForm.setDisplayName(accountName)
        guard !accountName.isEmpty else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        guard let matchingUser = await findMatchingUserOnScale(scale: scale, accountName: accountName) else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        guard await deleteMatchingUserFromScale(scale: scale, user: matchingUser) else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        await restartConnectionAndNavigate()
    }
    
    /// Gets the account name to restore, using the original name that exists on the scale
    /// (not the edited duplicateUserName, since restore should use the original account name)
    private func getAccountNameForRestore() -> String {
        // Use the original name from currentUser (the duplicate user on the scale) or firstName
        // This ensures restore uses the original name, not any edited name
        if let originalName = currentUser?.name, !originalName.isEmpty {
            return originalName.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return (firstName ?? "User").trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    /// Finds a matching user on the scale by comparing account name (handles name truncation)
    private func findMatchingUserOnScale(scale: Device, accountName: String) async -> DeviceUser? {
        let userListResult = await bluetoothService.getScaleUserList(for: scale, skipConnectionCheck: true)
        guard case .success(let allUsers) = userListResult else {
            return nil
        }
        
        let normalizedAccountName = accountName.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let maxScaleNameLength = 20 // Scale truncates names to 20 characters
        let truncatedAccountName = String(normalizedAccountName.prefix(maxScaleNameLength))
        
        // Try exact match first, then truncated match
        return allUsers.first(where: { $0.name.lowercased() == normalizedAccountName })
            ?? allUsers.first(where: { $0.name.lowercased() == truncatedAccountName })
    }
    
    /// Deletes a matching user from the scale during restore account flow
    private func deleteMatchingUserFromScale(scale: Device, user: DeviceUser) async -> Bool {
        guard let userToken = user.token, !userToken.isEmpty else {
            return false
        }
        
        scale.token = userToken
        let deleteResult = await bluetoothService.deleteDevice(scale, disconnect: false)
        
        switch deleteResult {
        case .success:
            return true
        case .failure:
            return false
        }
    }
    
    /// Determines which username value should be preserved when restarting the connection.
    /// - Parameter preservedUsername: The trimmed username currently entered in the form.
    /// - Returns: The username that should be kept visible to the user.
    private func resolveUsernameToPreserve(from preservedUsername: String) -> String {
        if !preservedUsername.isEmpty {
            return preservedUsername
        }
        
        if !duplicateUserName.isEmpty {
            return duplicateUserName
        }
        
        return firstName ?? "User"
    }
    
    /// Restarts the connection and navigates to the connecting step
    private func restartConnectionAndNavigate() async {
        // Preserve the current username value from form field before resetting
        // This ensures the username doesn't get cleared when restore account is tapped
        let preservedUsername = userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines)
        let usernameToPreserve = resolveUsernameToPreserve(from: preservedUsername)
        
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        scaleSetupError = .none
        await restartConnection()
        
        // Restore the username value after reset so it's still visible in the form
        userNameForm.setDisplayName(usernameToPreserve)
        duplicateUserName = usernameToPreserve
        
        navigateToStep(.connectingBluetooth)
    }
    
    /// Handles the delete user action from the max user count exceeded screen
    func handleDeleteUser(_ user: DeviceUser) {
        let alertStrings = alertLang.ConfirmDeleteUserAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message(user.name),
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.deleteButton, type: .danger) { [weak self] _ in
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
            setupScaleUsernameForm()
            
        case .scaleMode:
            // Pre-populate scale mode settings from attached preference
            Task { [weak self] in
                guard let self, let savedScale = self.savedScale else { return }
                if let preference = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                    await MainActor.run {
                        self.selectedScaleMode = preference.shouldMeasureImpedance ? .allBodyMetrics : .weightOnly
                        self.isHeartRateEnabled = preference.shouldMeasurePulse
                    }
                }
            }
            initialScaleModeSnapshot = selectedScaleMode
            initialHeartRateEnabledSnapshot = isHeartRateEnabled
        case .scaleMetrics:
            // Preload saved display metrics from an attached preference
            Task { [weak self] in
                guard let self else { return }
                if let savedScale = self.savedScale,
                   let preference = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                    await MainActor.run { 
                        self.selectedScaleMetrics = Array(preference.displayMetrics)
                        // Set initial snapshot to current saved state
                        self.initialScaleMetricsSnapshot = Array(preference.displayMetrics)
                        // If no saved snapshot exists, set it to current saved state
                        if self.savedScaleMetricsSnapshot == nil {
                            self.savedScaleMetricsSnapshot = Array(preference.displayMetrics)
                        }
                    }
                } else {
                    await MainActor.run { 
                        self.selectedScaleMetrics = ScaleMetrics.defaultMetricsKeys
                        // Set initial snapshot to default state
                        self.initialScaleMetricsSnapshot = ScaleMetrics.defaultMetricsKeys
                        // If no saved snapshot exists, set it to default state
                        if self.savedScaleMetricsSnapshot == nil {
                            self.savedScaleMetricsSnapshot = ScaleMetrics.defaultMetricsKeys
                        }
                    }
                }
            }
        case .dashboardMetrics:
            Task { [weak self] in
                guard let self else { return }
                await self.setupDashboardMetricsCustomization()
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
    
    /// Adds a customize settings item to the visited items set (tracks that user has opened this screen)
    func addSelectedCustomizeItem(_ item: String) {
        visitedCustomizeItems.insert(item)
    }
    
    /// Checks if a customize settings item has been visited
    func isCustomizeItemSelected(_ item: String) -> Bool {
        return visitedCustomizeItems.contains(item)
    }
    
    /// Handles the save action from the view settings screen
    private func handleViewSettingsAction() {
        switch currentCustomizeSetting {
        case .scaleUsername:
            // Validate the form first
            guard userNameForm.displayName.isValid else { return }
            
            // Update the attached scale preference with the new display name
            if let savedScale = savedScale {
                Task {
                    if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                        attached.displayName = userNameForm.displayName.value
                    }
                }
            }
            self.hasCustomizeChanges = true
            break
        case .scaleMode:
            // Update the attached preference with new scale mode settings
            if let savedScale = savedScale {
                Task {
                    if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                        attached.shouldMeasureImpedance = (selectedScaleMode == .allBodyMetrics)
                        attached.shouldMeasurePulse = isHeartRateEnabled
                    }
                }
            }
            self.hasCustomizeChanges = true
            break
        case .scaleMetrics:
            // Persist scale metrics changes immediately when Save is clicked
            if let savedScale = savedScale {
                Task {
                    if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                        attached.displayMetrics = selectedScaleMetrics
                        // Update the saved snapshot to reflect the new saved state
                        await MainActor.run {
                            self.savedScaleMetricsSnapshot = self.selectedScaleMetrics
                        }
                    }
                }
            }
            self.hasCustomizeChanges = true
            self.selectedCustomizeItems.insert(CustomizeSettingsItem.scaleMetrics.rawValue)
            break
        case .dashboardMetrics:
            // Save dashboard changes immediately when Save is clicked
            dashboardStore.saveChanges()
            // Mark that changes were made so the flow can treat it as updated
            self.hasCustomizeChanges = true
            self.selectedCustomizeItems.insert(CustomizeSettingsItem.dashboardMetrics.rawValue)
            // Post notification to refresh dashboard screen when user returns to it
            // Note: This notification will also trigger a reload in the customize screen,
            // but that's fine as it ensures both screens stay in sync
            NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
            // Cancel sync subscription after saving since we've already synced
            dashboardMetricsUpdatedCancellable?.cancel()
            dashboardMetricsUpdatedCancellable = nil
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
        
        // Cancel dashboard metrics sync subscription when navigating away
        dashboardMetricsUpdatedCancellable?.cancel()
        dashboardMetricsUpdatedCancellable = nil
        
        // Revert unsaved changes for each customize screen
        switch currentCustomizeSetting {
        case .dashboardMetrics:
            // Discard dashboard customization changes
            discardDashboardCustomization()
            // Reload dashboard configuration from API to ensure we have the latest state
            Task { [weak self] in
                guard let self else { return }
                await self.dashboardStore.reloadDashboardConfiguration(fullRefresh: true)
            }
        case .scaleMode:
            if let savedScale = savedScale {
                // Restore previously snapshotted values
                let originalMode = initialScaleModeSnapshot ?? (savedScale.r4ScalePreference?.shouldMeasureImpedance == true ? .allBodyMetrics : .weightOnly)
                let originalPulse = initialHeartRateEnabledSnapshot ?? (savedScale.r4ScalePreference?.shouldMeasurePulse ?? false)
                selectedScaleMode = originalMode
                isHeartRateEnabled = originalPulse
            }
        case .scaleUsername:
            if let original = initialDisplayNameSnapshot {
                userNameForm.setDisplayName(original)
                resetFormState()
            }
        case .scaleMetrics:
            // Revert scale metrics to the last saved state (not the initial state)
            // This preserves previously saved changes and only reverts unsaved changes
            if let savedState = savedScaleMetricsSnapshot {
                selectedScaleMetrics = savedState
            }
        default:
            break
        }
        
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
        // Check permissions before navigating to password step
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        if missingPermissions || noNetwork {
            // Show error when permissions are missing (similar to Android behavior)
            // Navigate back to gathering network which will show the error screen
            connectionState = .noNetworks
            navigateToStep(.gatheringNetwork)
            return
        }
        
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
                    // Navigate to connectingBluetooth first to maintain flow, then show the error
                    self.navigateToStep(.connectingBluetooth)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        self.connectionState = .failure
                    }
                }
            }
        }
    }
    
    /// Invoked from the *Try Again* button of `BluetoothConnectionView` and `WifiConnectionView` failure state.
    private func tryAgainButtonHandler(isFromBtConnection: Bool = false) {
        // Don't navigate if we're exiting
        guard !isExiting else { return }
        
        // Determine which step to navigate to based on the error type
        let targetStep: BtWifiScaleSetupStep
        if isFromBtConnection {
            if discoveredScale != nil {
                targetStep = .connectingBluetooth
            } else {
                targetStep = .wakeup
            }
        } else if scaleSetupError == .collectMeasurementFailed {
            targetStep = .stepOn
        } else if scaleSetupError == .updateSettingsFailed {
            targetStep = .customizeSettings
        } else {
            // Retry WiFi network fetch
            targetStep = .gatheringNetwork

            fetchWifiNetworksTask?.cancel()
            fetchWifiNetworksTask = nil

            isRefreshingWifiNetworks = true
            scaleSetupError = .none
            connectionState = .loading
        }
        
        // Clear error state for other error types after a delay
        if scaleSetupError != .collectMeasurementFailed && targetStep != .gatheringNetwork {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                self.scaleSetupError = .none
                self.connectionState = .loading
            }
        }
        
        navigateToStep(targetStep)
    }
    
    // MARK: - Step Change Handling
    private func handleStepChange() {
        // Don't perform step change actions if we're exiting
        guard !isExiting else {
            return
        }
        
        switch currentStep {
        case .intro:
            break
        case .permissions:
            break
        case .wakeup:
            self.pair()
        case .connectingBluetooth:
            self.connectionState = .loading
            Task {
                if discoveredScale != nil && discoveryEvent != nil {
                    await self.confirmPair()
                } else {
                    // Only set failure if we actually can't pair (missing device), not due to network
                    connectionState = .failure
                }
            }
        case .gatheringNetwork:
            // Don't fetch networks if we're showing a "No Networks Found" error
            if scaleSetupError == .noNetworkFound && !isRefreshingWifiNetworks {
                return
            }
            
            if scaleSetupError != .maxUserReached && scaleSetupError != .duplicatesFound {
                connectionState = .loading
                startNetworkScanTimeout()
            }
            
            // Skip fetch only if settings WiFi setup AND not refreshing
            let shouldSkipFetch = isSettingsWifiSetup && previousStep == .availableWifiList && !isRefreshingWifiNetworks
            
            if let savedScale = savedScale,
               scaleSetupError != .maxUserReached && scaleSetupError != .duplicatesFound,
               !shouldSkipFetch {
                // Cancel any existing fetch task
                fetchWifiNetworksTask?.cancel()
                fetchWifiNetworksTask = Task { [weak self] in
                    guard let self else { return }
                    // Reset refresh flag after starting the task
                    self.isRefreshingWifiNetworks = false
                    await self.fetchWifiNetworks(for: savedScale)
                }
            } else {
                // Reset refresh flag even if we skip fetch
                isRefreshingWifiNetworks = false
            }
        case .availableWifiList:
            if isSettingsWifiSetup {
                scaleSetupError = .none
                connectionState = .loading
            }
        case .wifiPassword:
            break
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
        case .customizeSettings:
            break
        case .stepOn:
            // Set skipCheckNetwork to true when entering stepOn screen to prevent network errors
            HTTPClient.shared.skipCheckNetwork = true
            
            // Cancel any existing stepOn timeout
            stepOnTimeoutTask?.cancel()
            
            Task {
                guard let savedScale = self.savedScale else { return }
                // Subscribe to live measurement updates and proceed when weight > 0
                await bluetoothService.startLiveMeasurement(for: savedScale)
                self.liveMeasurementSubscription = self.bluetoothService.liveMeasurementPublisher
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] liveEntry in
                        guard let self else { return }
                        
                        // Don't navigate if we're exiting, especially from stepOn
                        guard !self.isExiting && !self.isExitingFromStepOn else { return }
                        
                        if liveEntry.displayWeight > 0 && savedScale.broadcastIdString == liveEntry.broadcastId {
                            Task {
                                await self.bluetoothService.stopLiveMeasurement(for: savedScale)
                                self.cancelMeasurementSubscription()
                                self.cancelStepOnTimeout()
                                self.scaleSetupError = .none
                                self.moveToNextStep()
                            }
                        }
                    }
                
                // Auto-navigate from Step On screen after 3.5 minutes
                stepOnTimeoutTask = Task { [weak self] in
                    try? await Task.sleep(nanoseconds: 210 * 1_000_000_000)
                    guard let self, !Task.isCancelled, self.currentStep == .stepOn else { return }
                    
                    await MainActor.run {
                        // Don't navigate if we're exiting, especially from stepOn
                        guard !self.isExiting && !self.isExitingFromStepOn else { return }
                        // Auto-navigate only if Bluetooth is enabled and the scale is connected
                        let hasBluetoothPermissions = self.hasAllBtPermissions()
                        let isScaleConnected = self.savedScale?.isConnected == true

                        guard hasBluetoothPermissions && isScaleConnected else {
                            LoggerService.shared.log(
                                level: .info,
                                tag: self.tag,
                                message: "StepOn timeout: Skipping auto-navigation - Bluetooth disabled or scale not connected"
                            )
                            return
                        }                       
                        self.cancelStepOnTimeout()
                        self.scaleSetupError = .none
                        self.moveToNextStep()
                    }
                }
            }
        case .measurement:
            // Cancel stepOn timeout and reset skipCheckNetwork when moving to measurement screen
            cancelStepOnTimeout()
            // Cancel any existing measurement subscription and timeout
            cancelMeasurementSubscription()
            
            // Set up timeout for measurement collection
            measurementTimeoutTask = Task { [weak self] in
                guard let timeoutConstants = self?.timeoutConstants.bluetoothTimeoutNs else { return }
                try? await Task.sleep(nanoseconds: UInt64(timeoutConstants))
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    guard let self else { return }
                    // If we're still on measurement step, handle timeout
                    // This includes cases where subscription was cancelled due to Bluetooth being off
                    if self.currentStep == .measurement {
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
        case .scaleConnected:
            break
        default:
            // Reset skipCheckNetwork for all other steps (not stepOn)
            if previousStep == .stepOn && currentStep != .stepOn {
                cancelStepOnTimeout()
            }
            break
        }
    }
    
    /// Sets connectionState while blocking network-related errors during Bluetooth pairing
    private func setConnectionState(_ newState: ConnectionState, allowNetworkErrors: Bool = true) {
        guard currentStep != .connectingBluetooth ||
            (newState != .noNetworks && (allowNetworkErrors || newState != .failure)) else {
            return
        }

        connectionState = newState
    }
    
    /// Handles permission changes during the setup flow
    /// Matches Android behavior: only show WiFi errors when on WiFi-related steps AND scale is connected
    private func handlePermissionChange() {
        // Don't handle permission changes if we're exiting
        guard !isExiting else { return }
        // Skip all network checks during Bluetooth pairing
        if currentStep == .connectingBluetooth {
            // Never show network-related errors while pairing
            if connectionState == .noNetworks {
                connectionState = .loading
            }
            return
        }
        
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        // Skip network checks for duplicate user screen (restore account flow)
        // Restore account is a Bluetooth-only operation and doesn't require WiFi/network
        if scaleSetupError == .duplicatesFound && currentStep == .gatheringNetwork {
            return
        }
        
        // For wakeup step, navigate to permissions if permissions or network are missing
        if (missingPermissions || noNetwork) && currentStep == .wakeup {
            resetDiscoveryState()
            navigateToStep(.permissions)
            return
        }
        
        guard missingPermissions || noNetwork else { return }
        
        // Only handle errors for steps that have been reached
        switch currentStep {
        case .intro:
            break
        case .gatheringNetwork:
            // Skip network checks when showing duplicate user screen (restore account flow)
            // Restore account is a Bluetooth-only operation and doesn't require WiFi/network
            if scaleSetupError == .duplicatesFound {
                return
            }
            if savedScale != nil {
                cancelNetworkScanTimeout()
                connectionState = .noNetworks
                scaleSetupError = .noNetworkFound
                // Cancel any ongoing WiFi network fetch to prevent navigation back to WiFi list
                fetchWifiNetworksTask?.cancel()
            } else {
                resetDiscoveryState()
                navigateToStep(.permissions)
            }
        case .availableWifiList:
            if noNetwork {
                // Navigate back to gathering network screen to show "No Networks Found" error
                setConnectionState(.noNetworks, allowNetworkErrors: false)
                scaleSetupError = .noNetworkFound
                navigateToStep(.gatheringNetwork)
            }
            break
        case .wifiPassword, .connectingWifi:
            // If Wi-Fi setup has already succeeded, don't navigate back to Wi-Fi list
            // The scheduled navigation will proceed to the next step
            if currentStep == .connectingWifi && connectionState == .success {
                return
            }
            if savedScale != nil {
                scaleSetupError = .wifiConnectionFailed
                if currentStep != .availableWifiList {
                    navigateToStep(.availableWifiList)
                }
            } else {
                resetDiscoveryState()
                navigateToStep(.permissions)
            }
            
        case .stepOn where scaleSetupError != .updateSettingsFailed:
            // Skip network checks during stepOn screen - network access is not required for collecting measurements
            let bluetoothSwitchOff =
                permissionsService.getPermissionState(.BLUETOOTH_SWITCH) != .ENABLED

            if bluetoothSwitchOff || missingPermissions {
                cancelMeasurementSubscription()
                cancelStepOnTimeout()
                showBluetoothTurnedOffAlert()
            }
            break

        case .measurement:
            let bluetoothSwitchOff =
                permissionsService.getPermissionState(.BLUETOOTH_SWITCH) != .ENABLED

            if bluetoothSwitchOff {
                newEntrySubscription?.cancel()
                newEntrySubscription = nil
                liveMeasurementSubscription?.cancel()
                liveMeasurementSubscription = nil

                // Keep timeout running so error screen appears
                showBluetoothTurnedOffAlert()
            }
            break
            
        case .updateSettings:
            let bluetoothSwitchOff =
                permissionsService.getPermissionState(.BLUETOOTH_SWITCH) != .ENABLED
            
            if !bluetoothSwitchOff {
                Task { [weak self] in
                    guard let self = self, let savedScale = self.savedScale else { return }
                    
                    do {
                        try await self.scaleService.updateAllScalesStatus([savedScale])
                    } catch {
                        LoggerService.shared.log(level: .error, tag: self.tag, message: "BtWifiScaleSetupStore.handlePermissionChange(.updateSettings): failed to update scale status for id \(savedScale.id): \(error.localizedDescription)")
                        return
                    }
                    
                    do {
                        guard let refreshedScale = try await self.scaleService.getDevice(by: savedScale.id) else {
                            LoggerService.shared.log(level: .error, tag: self.tag, message: "BtWifiScaleSetupStore.handlePermissionChange(.updateSettings): device not found for id \(savedScale.id)")
                            return
                        }
                        await MainActor.run {
                            self.savedScale = refreshedScale
                            self.bluetoothService.syncDevices([refreshedScale])
                        }
                    } catch {
                        LoggerService.shared.log(level: .error, tag: self.tag, message: "BtWifiScaleSetupStore.handlePermissionChange(.updateSettings): failed to refresh scale device for id \(savedScale.id): \(error.localizedDescription)")
                    }
                }
            }
            break
            
        default:
            // For other steps, don't automatically navigate
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
                    // Don't set currentUserName here - we want to show that this name IS a duplicate
                    userNameForm.setCurrentUserName(nil)
                }
                
                // Convert DeviceUser list to ScaleUser list for form validation
                let scaleUsers = userList.map { deviceUser in
                    ScaleUser(name: deviceUser.name, token: deviceUser.token)
                }
                userNameForm.updateUserList(scaleUsers)
                userNameForm.displayName.markAsPristine()
                userNameForm.displayName.markAsUntouched()
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
            case .inputDataError:
                LoggerService.shared.log(level: .error, tag: tag, message: "Input data error: \(response)")
                connectionState = .failure
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
            let deviceInfoResult = await bluetoothService.getDeviceInfo(for: scale, skipConnectionCheck: true)
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
            
            let isDashboardFour = isDashboardTypeFour
            
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
            
            // Ensure connection status is updated after sync completes
            // This prevents UI flicker when navigating back to MyScalesScreen
            do {
                try await scaleService.updateAllScalesStatus()
            } catch {
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to update scales status after save: \(error.localizedDescription)")
            }
            
            Task {
                await self.pushNotificationService.setupPushNotifications(isFromScaleSetup: true)
            }
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully: \(savedScale.id)")
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            
            if isDashboardFour {
                await upgradeDashboardTypeFrom4To12PreservingRemovalState()
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
        // Don't fetch if we're exiting or task is cancelled
        guard !isExiting, !Task.isCancelled else { return }
        
        // Check permissions before attempting to fetch WiFi networks
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        if missingPermissions || noNetwork {
            LoggerService.shared.log(level: .error, tag: tag, message: "Cannot fetch WiFi networks: permissions missing or network unavailable")
            await MainActor.run {
                guard !self.isExiting, !Task.isCancelled else { return }
                self.setConnectionState(.noNetworks, allowNetworkErrors: false)
            }
            return
        }
        
        // Set loading state
        await MainActor.run {
            guard !self.isExiting, !Task.isCancelled else { return }
            self.connectionState = .loading
        }
        
        do {
            // Check cancellation before starting network fetch
            guard !isExiting, !Task.isCancelled else { return }
            
            // Get connected WiFi SSID first
            let connectedSSIDResult = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
            
            // Check cancellation after async operation
            guard !isExiting, !Task.isCancelled else { return }
            
            var connectedSSID: String?
            switch connectedSSIDResult {
            case .success(let ssid):
                connectedSSID = ssid.isEmpty ? nil : ssid
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get connected WiFi SSID: \(error.localizedDescription)")
                connectedSSID = nil
            }
            
            // Check cancellation before next async operation
            guard !isExiting, !Task.isCancelled else { return }
            
            // Get WiFi networks list
            let wifiListResult = await bluetoothService.getWifiList(for: scale)
            
            // Check cancellation after async operation
            guard !isExiting, !Task.isCancelled else { return }
            
            var networks: [WifiDetails] = []
            switch wifiListResult {
            case .success(let wifiList):
                networks = wifiList
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get WiFi networks: \(error.localizedDescription)")
                throw error
            }
            
            await MainActor.run {
                // Don't navigate if we're exiting or task is cancelled
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Check Wi-Fi status again before navigating - if Wi-Fi was turned off during fetch, don't navigate
                let noNetwork = !self.networkMonitor.isConnected
                if noNetwork {
                    // Wi-Fi was turned off during fetch, stay on error screen
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                    self.scaleSetupError = .noNetworkFound
                    return
                }
                
                // Cancel timeout task since we successfully fetched networks
                self.stepTimerTask?.cancel()
                self.stepTimerTask = nil
                
                self.wifiNetworks = networks
                
                // Find connected network in the list
                if let connectedSSID = connectedSSID {
                    self.connectedWifiNetwork = WifiDetails(macAddress: "", ssid: connectedSSID, rssi: 0)
                } else {
                    self.connectedWifiNetwork = nil
                }
                
                // Double-check we're not exiting before updating states and navigating
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Check if no networks were found
                if networks.isEmpty {
                    self.scaleSetupError = .noNetworkFound
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                } else {
                    self.scaleSetupError = .none
                    self.connectionState = .success
                }
                
                // Final check before navigation - prevent navigation if exiting
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Navigate to available WiFi list
                self.navigateToStep(.availableWifiList)
            }
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi networks: \(networks.count) networks found")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi networks: \(error.localizedDescription)")
            await MainActor.run {
                // Don't update state if we're exiting or task is cancelled
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Check if failure is due to missing permissions
                let missingPermissions = !self.hasAllBtPermissions()
                let noNetwork = !self.networkMonitor.isConnected
                
                if missingPermissions || noNetwork {
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                } else {
                    self.setConnectionState(.failure, allowNetworkErrors: false)
                    self.scaleSetupError = .noNetworkFound
                }
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
        let result = await bluetoothService.getDeviceInfo(for: scale, skipConnectionCheck: true)
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
            // Disconnect device to prevent rediscovery loop (scanning continues)
            if let broadcastId = event.device.broadcastIdString, !broadcastId.isEmpty {
                Task {
                    // Skip this device to prevent rediscovery loop
                    _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId, considerForSession: false)
                }
            }
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
                    guard let self = self else { return }
                    // Perform proper cleanup before dismissing
                    self.performExitCleanup()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Deletes duplicate users from the scale
    private func deleteUsers() async {
        guard let scale = discoveredScale else {
            return
        }
        
        // Delete all users in the duplicate list
        for user in duplicateList {
            guard let userToken = user.token else {
                continue
            }
            
            scale.token = userToken
            _ = await bluetoothService.deleteDevice(scale, disconnect: false)
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
        
        // Ensure we have a discovered scale and discovery event for re-pairing
        guard discoveredScale != nil, discoveryEvent != nil else {
            return
        }
        
        // Reset connection state to loading to trigger pairing
        connectionState = .loading
    }
    
    private func getUserList() async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - no discovered scale")
            return
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale, skipConnectionCheck: true)
        switch result {
        case .success(let users):
            // Filter out the current scale token
            self.userList = users.filter { user in
                user.token != scale.token
            }
            LoggerService.shared.log(level: .info, tag: tag, message: "getUserList - retrieved \(self.userList.count) users")
            
            // Update form validation with new user list if we're on username customization
            if currentCustomizeSetting == .scaleUsername {
                await MainActor.run {
                    let scaleUsers = self.userList.map { deviceUser in
                        ScaleUser(name: deviceUser.name, token: deviceUser.token)
                    }
                    self.userNameForm.updateUserList(scaleUsers)
                    // Ensure current user name is set for duplicate check exclusion
                    if self.userNameForm.currentUserName == nil {
                        let currentName = self.initialDisplayNameSnapshot ?? self.firstName ?? "User"
                        self.userNameForm.setCurrentUserName(currentName)
                    }
                    // Reset form state to pristine/untouched after updating user list
                    // This ensures errors don't show until user interacts with the field
                    self.userNameForm.displayName.markAsPristine()
                    self.userNameForm.displayName.markAsUntouched()
                    // Trigger validation and update Next button state
                    self.updateNextEnabled()
                }
            }
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
            let saveScaleMode = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleModes.rawValue)
            let saveScaleUsername = selectedCustomizeItems.contains(CustomizeSettingsItem.userName.rawValue)
            
            // Get current preference or create default
            let currentPreference: R4ScalePreference = await {
                if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                    return attached
                }
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
                displayName: currentPreference.displayName,
                displayMetrics: currentPreference.displayMetrics, // Use current preference metrics (already updated when Save was clicked)
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
    
    /// Sets up scale username form with initial values and async preference loading
    private func setupScaleUsernameForm() {
        let initialDisplayName = firstName ?? "User"
        userNameForm.setDisplayName(initialDisplayName)
        initialDisplayNameSnapshot = initialDisplayName
        userNameForm.setCurrentUserName(initialDisplayName)
        resetFormState()
        
        Task { [weak self] in
            guard let self else { return }
            if self.userList.isEmpty {
                await self.getUserList()
            }
            
            var displayName = self.firstName ?? "User"
            if let savedScale = self.savedScale,
               let attached = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                displayName = attached.displayName
            }
            
            await MainActor.run {
                if displayName != self.userNameForm.displayName.value {
                    self.userNameForm.setDisplayName(displayName)
                    self.initialDisplayNameSnapshot = displayName
                    self.userNameForm.setCurrentUserName(displayName)
                }
                
                let scaleUsers = self.userList.map { deviceUser in
                    ScaleUser(name: deviceUser.name, token: deviceUser.token)
                }
                self.userNameForm.updateUserList(scaleUsers)
                self.resetFormState()
                self.updateNextEnabled()
            }
        }
    }
    
    /// Resets form state to pristine/untouched
    private func resetFormState() {
        userNameForm.displayName.markAsPristine()
        userNameForm.displayName.markAsUntouched()
    }
    
    /// Starts timeout for network scan to prevent hanging
    private func startNetworkScanTimeout() {
        stepTimerTask?.cancel()
        stepTimerTask = Task { [weak self] in
            guard let timeout = self?.timeoutConstants.bluetoothTimeoutNs else { return }
            try? await Task.sleep(nanoseconds: UInt64(timeout))
            guard let self, !Task.isCancelled else { return }

            await MainActor.run {
                guard self.currentStep == .gatheringNetwork,
                      self.scaleSetupError == .none,
                      self.connectionState == .loading else { return }

                LoggerService.shared.log(level: .error, tag: self.tag, message: "Network scan timed out")
                self.connectionState = .failure
                self.scaleSetupError = .noNetworkFound
            }
        }
    }
    
    /// Cancels network scan timeout
    private func cancelNetworkScanTimeout() {
        stepTimerTask?.cancel()
        stepTimerTask = nil
    }
    
    /// Cancels measurement subscription and timeout task
    private func cancelMeasurementSubscription() {
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
        liveMeasurementSubscription?.cancel()
        liveMeasurementSubscription = nil
        measurementTimeoutTask?.cancel()
        measurementTimeoutTask = nil
    }
    
    private func cancelStepOnTimeout() {
        stepOnTimeoutTask?.cancel()
        stepOnTimeoutTask = nil
        // Reset skipCheckNetwork when leaving stepOn screen
        HTTPClient.shared.skipCheckNetwork = false
    }
    
    private func navigateToStep(_ step: BtWifiScaleSetupStep, delay: TimeInterval = 0) {
        // Don't navigate if we're exiting, especially from stepOn
        guard !isExiting && !isExitingFromStepOn else { return }
        
        if currentStep == .gatheringNetwork && step != .gatheringNetwork {
            cancelNetworkScanTimeout()
        }
        if let stepIndex = steps.firstIndex(of: step) {
            currentStepIndex = stepIndex
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
    func updateNextEnabled() {
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
            // Enable save button only when there's a duplicate error and username is valid and changed
            if scaleSetupError == .duplicatesFound {
                let current = removeWhiteSpace(userNameForm.displayName.value)
                let initial = removeWhiteSpace(firstName ?? "User")
                isNextEnabled = userNameForm.displayName.isValid && current != initial
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
            // Enable Save only when there is a real change for each setting
            switch currentCustomizeSetting {
            case .scaleUsername:
                let current = removeWhiteSpace(userNameForm.displayName.value)
                let initial = removeWhiteSpace(initialDisplayNameSnapshot ?? (firstName ?? "User"))
                isNextEnabled = userNameForm.displayName.isValid && current != initial
            case .scaleMetrics:
                // Check if current state differs from the last saved state
                let hasScaleMetricsChanged = savedScaleMetricsSnapshot != nil && savedScaleMetricsSnapshot != selectedScaleMetrics
                isNextEnabled = hasScaleMetricsChanged
            case .scaleMode:
                let changed = (selectedScaleMode != initialScaleModeSnapshot) || (isHeartRateEnabled != (initialHeartRateEnabledSnapshot ?? false))
                isNextEnabled = changed
            case .dashboardMetrics:
                isNextEnabled = hasDashboardCustomizationChanged()
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
    
    /// Snapshots the current dashboard state for change detection
    private func snapshotDashboardState() {
        initialDashboardMetricLabelsSnapshot = dashboardStore.metricsManager.state.metrics.map { $0.label }
        initialDashboardRemovedMetricsSnapshot = dashboardStore.state.ui.removedMetrics
        initialDashboardRemovedStreaksSnapshot = dashboardStore.state.ui.removedStreaks
        initialDashboardStreakOrderSnapshot = dashboardStore.state.ui.streakGridOrder
        initialDashboardGoalCardRemovedSnapshot = dashboardStore.state.ui.isGoalCardRemoved
        initialDashboardGoalCardPositionSnapshot = dashboardStore.state.ui.goalCardPosition
    }
    
    /// Returns true if dashboard customization has changed compared to initial snapshot
    private func hasDashboardCustomizationChanged() -> Bool {
        let state = dashboardStore.metricsManager.state.metrics.map { $0.label }
        let removed = dashboardStore.state.ui.removedMetrics
        let removedStreaks = dashboardStore.state.ui.removedStreaks
        let order = dashboardStore.state.ui.streakGridOrder
        let goalRemoved = dashboardStore.state.ui.isGoalCardRemoved
        let goalPos = dashboardStore.state.ui.goalCardPosition
        
        return (initialDashboardMetricLabelsSnapshot != nil && initialDashboardMetricLabelsSnapshot != state) ||
               (initialDashboardRemovedMetricsSnapshot != nil && initialDashboardRemovedMetricsSnapshot != removed) ||
               (initialDashboardRemovedStreaksSnapshot != nil && initialDashboardRemovedStreaksSnapshot != removedStreaks) ||
               (initialDashboardStreakOrderSnapshot != nil && initialDashboardStreakOrderSnapshot != order) ||
               (initialDashboardGoalCardRemovedSnapshot != nil && initialDashboardGoalCardRemovedSnapshot != goalRemoved) ||
               (initialDashboardGoalCardPositionSnapshot != nil && initialDashboardGoalCardPositionSnapshot != goalPos)
    }
    
    /// Discards dashboard customization changes by canceling edit mode
    private func discardDashboardCustomization() {
        dashboardStore.cancelEdit()
        // Remove from selected items (unsaved changes), but keep in visited items (checkmark stays)
        selectedCustomizeItems.remove(CustomizeSettingsItem.dashboardMetrics.rawValue)
        // Only clear hasCustomizeChanges if no other settings have been changed
        hasCustomizeChanges = !selectedCustomizeItems.isEmpty
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
    
    // MARK: - Dashboard Metrics Upgrade
    
    /// Upgrades dashboard type from 4 to 12 while preserving removal state of original 4 metrics.
    /// This ensures that if user had removed some of the original 4 metrics (BMI, body fat, muscle, water),
    /// those remain removed after the upgrade, while the 8 new metrics are all enabled.
    private func upgradeDashboardTypeFrom4To12PreservingRemovalState() async {
        await dashboardStore.reloadDashboardConfiguration(fullRefresh: true)
        
        let originalActiveMetricsCount = dashboardStore.metricsManager.state.activeMetricsCount
        let originalMetrics = dashboardStore.metricsManager.state.metrics
        let originalFourLabels = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
        
        
        let enabledOriginalMetrics = Array(originalMetrics.prefix(originalActiveMetricsCount))
        let removedOriginalMetrics = Array(originalMetrics.dropFirst(originalActiveMetricsCount))
        let enabledOriginalLabels = Set(enabledOriginalMetrics.map { $0.label })
        let removedOriginalLabels = Set(removedOriginalMetrics.map { $0.label })
        
        LoggerService.shared.log(level: .info, tag: tag, message: "R4 setup: Original 4 metrics - Enabled: \(enabledOriginalLabels), Removed: \(removedOriginalLabels)")
        
        do {
            let apiRepo = AccountRepositoryAPI()
            _ = try await apiRepo.patchDashboardType(.dashboard12)
            try await accountService.refreshAccount(accountId: accountService.activeAccount?.accountId)
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "R4 setup: Failed to update dashboard type on server: \(error.localizedDescription)")
        }
        
        await dashboardStore.reloadDashboardConfiguration(fullRefresh: true)
        
        await MainActor.run {
            dashboardStore.metricsManager.updateDashboardType(.dashboard12)
            dashboardStore.state.metrics.dashboardType = .dashboard12
            
            let newMetricsLabels = [
                DashboardStrings.heartBpm, DashboardStrings.bone, DashboardStrings.visceralFat,
                DashboardStrings.subFat, DashboardStrings.protein, DashboardStrings.skelMuscle,
                DashboardStrings.bmrKcal, DashboardStrings.metAge
            ]
            
            let originalMetricsDef = [
                (DashboardStrings.placeholder, DashboardStrings.bmi, nil, nil, AppAssets.bmiIcon),
                (DashboardStrings.placeholder, DashboardStrings.bodyFat, DashboardStrings.percentageUnitSymbol, nil, AppAssets.bodyFatIcon),
                (DashboardStrings.placeholder, DashboardStrings.muscle, DashboardStrings.percentageUnitSymbol, nil, AppAssets.muscleIcon),
                (DashboardStrings.placeholder, DashboardStrings.water, DashboardStrings.percentageUnitSymbol, nil, AppAssets.waterIcon),
                (DashboardStrings.placeholder, DashboardStrings.heartBpm, DashboardStrings.bpmUnitSymbol, nil, AppAssets.heartIcon),
                (DashboardStrings.placeholder, DashboardStrings.bone, DashboardStrings.percentageUnitSymbol, nil, AppAssets.boneIcon),
                (DashboardStrings.placeholder, DashboardStrings.visceralFat, nil, DashboardStrings.visceralFatPre, AppAssets.visceralFatIcon),
                (DashboardStrings.placeholder, DashboardStrings.subFat, DashboardStrings.percentageUnitSymbol, nil, AppAssets.subcutaneousFatIcon),
                (DashboardStrings.placeholder, DashboardStrings.protein, DashboardStrings.percentageUnitSymbol, nil, AppAssets.proteinIcon),
                (DashboardStrings.placeholder, DashboardStrings.skelMuscle, DashboardStrings.percentageUnitSymbol, nil, AppAssets.skeletalMuscleIcon),
                (DashboardStrings.placeholder, DashboardStrings.bmrKcal, DashboardStrings.kcalUnitSymbol, nil, AppAssets.bmrIcon),
                (DashboardStrings.placeholder, DashboardStrings.metAge, DashboardStrings.metAgeUnit, nil, AppAssets.ageIcon)
            ]
            
            var orderedMetrics: [MetricItem] = []
            
            for label in originalFourLabels where enabledOriginalLabels.contains(label) {
                if let metricDef = originalMetricsDef.first(where: { $0.1 == label }) {
                    orderedMetrics.append(MetricItem(
                        value: metricDef.0, label: metricDef.1, unit: metricDef.2,
                        preLabel: metricDef.3, icon: metricDef.4
                    ))
                }
            }
            
            for newLabel in newMetricsLabels {
                if let metricDef = originalMetricsDef.first(where: { $0.1 == newLabel }) {
                    orderedMetrics.append(MetricItem(
                        value: metricDef.0, label: metricDef.1, unit: metricDef.2,
                        preLabel: metricDef.3, icon: metricDef.4
                    ))
                }
            }
            
            for label in originalFourLabels where removedOriginalLabels.contains(label) {
                if let metricDef = originalMetricsDef.first(where: { $0.1 == label }) {
                    orderedMetrics.append(MetricItem(
                        value: metricDef.0, label: metricDef.1, unit: metricDef.2,
                        preLabel: metricDef.3, icon: metricDef.4
                    ))
                }
            }
            
            dashboardStore.metricsManager.state.metrics = orderedMetrics
            dashboardStore.metricsManager.state.activeMetricsCount = enabledOriginalLabels.count + 8
            dashboardStore.syncRemovalStateFromMetricsManager()
            
            LoggerService.shared.log(level: .info, tag: tag, message: "R4 setup: Upgraded to dashboard12. Enabled original: \(enabledOriginalLabels.count), Removed: \(removedOriginalLabels.count), Active count: \(enabledOriginalLabels.count + 8)")
        }
        
        do {
            // The removal state is managed by the metricsManager's activeMetricsCount and the ordering of metrics.
            // We intentionally pass an empty array for removedMetrics here, as the actual removal state is derived from metric ordering.
            try await dashboardStore.metricsManager.saveMetricsToAPI(removedMetrics: [])
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "R4 setup: Failed to save metrics to API: \(error.localizedDescription)")
        }
    }
    
    /// Checks if the current dashboard type is dashboard4
    private var isDashboardTypeFour: Bool {
        let currentDashboardType = accountService.activeAccount?.dashboardSettings?.dashboardType
        let result = (currentDashboardType == "dashboard_4_metrics" || 
                currentDashboardType == "dashboard4") &&
                dashboardStore.effectiveDashboardType == .dashboard4
        return result
    }
    
    /// Sets up dashboard metrics customization screen with proper state management
    // First pairing upgrades dashboard and sets default order; subsequent pairings preserve current order
    private func setupDashboardMetricsCustomization() async {
        let isDashboardFour = isDashboardTypeFour
        
        if isDashboardFour {
            await upgradeDashboardTypeFrom4To12PreservingRemovalState()
            // Ensure streak data is refreshed and progress metrics are loaded for dashboard 4 upgrade
            try? await dashboardStore.streakManager.refreshStreakData()
            await dashboardStore.loadProgressMetricsFromAccount()
        } else {
            // Preserve dashboard order on re-pairing; reload only if metrics are empty
            let currentMetricsCount = await MainActor.run {
                dashboardStore.metricsManager.state.metrics.count
            }
            if currentMetricsCount == 0 {
                await dashboardStore.reloadDashboardConfiguration(fullRefresh: true)
            }
        }
        
        await MainActor.run {
            // Only set default order if metrics are truly empty (shouldn't happen for R4 scales)
            if dashboardStore.metricsManager.state.metrics.isEmpty {
                dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)
            }
            
            dashboardStore.beginEdit()
            dashboardStore.state.ui.isEditMode = true
            snapshotDashboardState()
        }
        
        setupDashboardMetricsSubscriptions()
    }
    
    /// Sets up subscriptions for dashboard metrics customization screen
    private func setupDashboardMetricsSubscriptions() {
        dashboardStoreCancellable?.cancel()
        dashboardStoreCancellable = dashboardStore.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                if self.currentStep == .viewSettings && self.currentCustomizeSetting == .dashboardMetrics {
                    self.updateNextEnabled()
                }
            }
        setupDashboardMetricsSync()
    }
    
    // MARK: - Dashboard Synchronization
    
    /// Sets up synchronization between customize screen and main dashboard
    /// When dashboard metrics are updated in the main dashboard, this ensures
    /// the customize screen reflects those changes immediately
    private func setupDashboardMetricsSync() {
        // Cancel existing subscription if any
        dashboardMetricsUpdatedCancellable?.cancel()
        
        // Subscribe to dashboard metrics updated notification

        dashboardMetricsUpdatedCancellable = NotificationCenter.default.publisher(for: .dashboardMetricsUpdated)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                // Only reload if we're currently on the dashboard metrics customization screen
                if self.currentStep == .viewSettings && 
                   self.currentCustomizeSetting == .dashboardMetrics && 
                   !self.isExiting {
                }
            }
    }
    
    // MARK: - Cleanup Methods
    
    /// Checks if "Set a Goal" modal should be shown after setup completes
    /// This handles the case where the 3rd entry was taken during setup
    private func checkGoalModalAfterSetup() {
        Task { @MainActor [weak self] in
            guard let self else { return }
            // Add delay of 1.5 seconds after setup is closed, similar to other scale setups
            try? await Task.sleep(nanoseconds: 1_500_000_000) // 1.5 seconds
            do {
                let entryCount = try await self.entryService.getEntryCount()
                await self.goalAlertService.checkSetGoalCard(entryCount: entryCount)
            } catch {
                // Silently ignore errors - goal modal check is not critical
            }
        }
    }
    
    /// Cleans up the store and breaks any retain cycles
    func cleanup() {
        // Clear the dismiss action to break retain cycle
        dismissAction = nil
        
        // Cancel all tasks
        fetchWifiNetworksTask?.cancel()
        fetchWifiNetworksTask = nil
        
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        networkFormCancellable?.cancel()
        networkFormCancellable = nil
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
        liveMeasurementSubscription?.cancel()
        liveMeasurementSubscription = nil
        measurementTimeoutTask?.cancel()
        measurementTimeoutTask = nil
        stepOnTimeoutTask?.cancel()
        stepOnTimeoutTask = nil
        stepTimerTask?.cancel()
        stepTimerTask = nil
        dashboardStoreCancellable?.cancel()
        dashboardStoreCancellable = nil
        dashboardMetricsUpdatedCancellable?.cancel()
        dashboardMetricsUpdatedCancellable = nil
        
        // Cancel all cancellables
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()

        bluetoothService.isSetupInProgress = false
        
        // Clear other references
        discoveredScale = nil
        discoveryEvent = nil
        savedScale = nil
        // Re-apply skipped devices to BLE SDK, excluding paired scales
        bluetoothService.reapplySkipDevicesExcludingPaired()
        
        DispatchQueue.main.async { [weak self] in
            self?.isExiting = false
            self?.isExitingFromStepOn = false
        }
    }
    
    /// Resumes scanning and syncs all paired devices after setup exits
    private func resumeScanningAndSyncDevices() async {
        bluetoothService.resumeSmartScan(clearOnlyPairing: false)
        
        do {
            try await scaleService.updateAllScalesStatus()
            bluetoothService.syncDevices([])
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to resume scanning and sync devices: \(error.localizedDescription)")
        }
    }
    
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
    
    // MARK: - Network Comparison Methods
    /// Check if two networks are the same by comparing SSID and MAC address
    func isSameNetwork(_ network1: WifiDetails, _ network2: WifiDetails) -> Bool {
        func cleanSSID(_ ssid: String?) -> String {
            ssid?.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "\0", with: "")
                .lowercased() ?? ""
        }
        func cleanMAC(_ mac: String) -> String {
            mac.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        }
        
        let ssid1 = cleanSSID(network1.ssid)
        let ssid2 = cleanSSID(network2.ssid)
        let mac1 = cleanMAC(network1.macAddress)
        let mac2 = cleanMAC(network2.macAddress)
        
        return (!ssid1.isEmpty && ssid1 == ssid2) || (!mac1.isEmpty && mac1 == mac2)
    }
}
