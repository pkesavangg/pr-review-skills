//
//  BtWifiScaleSetupStore.swift
//  meApp
//
//  Created by Cursor AI on 12/01/25.
//

import Combine
// This file intentionally aggregates all BtWifi scale setup orchestration logic.
// Breaking it into smaller files would fragment the multi-step flow management and reduce maintainability.
// The updateNextEnabled function has high complexity due to multiple step-specific validation rules.
import Foundation
import SwiftUI

/// Store responsible for orchestrating the BtWifi scale setup multi-step flow.
@MainActor
final class BtWifiScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector var notificationService: NotificationHelperService
    /// Centralised permission handling service.
    @Injector var permissionsService: PermissionsService
    /// Bluetooth service for device discovery
    @Injector var bluetoothService: BluetoothService
    /// Account service for account operations
    @Injector var accountService: AccountService
    /// Scale service for scale-related operations
    @Injector var wifiScaleService: WifiScaleService
    
    @Injector var scaleService: ScaleService
    @Injector var pushNotificationService: PushNotificationService
    @Injector var entryService: EntryService
    @Injector var goalAlertService: GoalAlertService
    
    let networkMonitor = NetworkMonitor.shared
    let bluetoothSetupManager: BluetoothSetupManaging
    let wifiSetupManager: WifiSetupManaging
    let setupCoordinator: ScaleSetupCoordinating
    let setupValidationService: SetupValidationServicing
    
    /// Resolved scale metadata used across the setup flow.
    var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?
    /// Discovered scale information
    var discoveredScale: Device?
    /// Discovery event from Bluetooth service
    var discoveryEvent: DeviceDiscoveryEvent?
    /// Cached scale token to avoid repeated API calls
    var scaleToken: String?
    /// Cached first name from active account
    var firstName: String?

    /// Exposed via a read-only computed property so views (e.g. `BtWifiScaleSetupScreen`) can react accordingly.
    var isWifiSetupOnly: Bool = false
    /// Public accessor used by views to know whether the current flow is Wi-Fi-only (opened from Settings).
    var isWifiSetupOnlyMode: Bool { isWifiSetupOnly }
    
    /// True when WiFi setup is opened from Settings (not initial scale setup)
    var isSettingsWifiSetup: Bool {
        isWifiSetupOnly && savedScale != nil
    }
    
    /// Indicates if this is a reconnect flow
    var isReconnect: Bool = false
    /// Public accessor for reconnect mode
    var isReconnectMode: Bool { isReconnect }
    
    /// Indicates if this is handling a duplicate user error
    var isDuplicated: Bool = false
    /// Public accessor for duplicate user mode
    var isDuplicatedMode: Bool { isDuplicated }
    
    // MARK: - Private
    var cancellables = Set<AnyCancellable>()
    /// Active subscription to the Bluetooth discovery publisher – only used during the *wake-up* step.
    var deviceDiscoveryCancellable: AnyCancellable?
    /// Active subscription to the network form changes
    var networkFormCancellable: AnyCancellable?
    /// Active subscription to new entry events during measurement
    var newEntrySubscription: AnyCancellable?
    /// Active subscription to live measurement data during step-on phase
    var liveMeasurementSubscription: AnyCancellable?
    /// Task handling measurement timeout
    var measurementTimeoutTask: Task<Void, Never>?
    /// Task handling stepOn screen timeout to auto-navigate to measurement screen
    var stepOnTimeoutTask: Task<Void, Never>?
    /// Task handling WiFi networks fetch - stored so we can cancel it when exiting
    var fetchWifiNetworksTask: Task<Void, Never>?
    
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
                    Task { @MainActor [weak self] in
                        guard let self, self.isExiting || self.isExitingFromStepOn else {
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
    var previousStep: BtWifiScaleSetupStep = .intro
    /// Flag to track if we're refreshing WiFi networks (to bypass skip logic)
    var isRefreshingWifiNetworks: Bool = false
    
    // Flag to prevent error screens from showing during exit
    // Published so SwiftUI reacts immediately when it changes
    @Published var isExiting: Bool = false
    
    // Flag to specifically prevent navigation from stepOn screen
    var isExitingFromStepOn: Bool = false
    
    // Flag to prevent recursive calls in currentStepIndex didSet
    var isRevertingStepIndex: Bool = false
    
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
    @Published var duplicateUserLastActiveAt: Int64?
    
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
    @Published var errorCode: String?
    
    /// Current customize setting being viewed
    @Published var currentCustomizeSetting: CustomizeSettings = .none
    
    /// Tracks if any changes were made in customize settings
    @Published var hasCustomizeChanges: Bool = false
    
    /// Tracks if any customization settings have been saved (used to determine navigation to updateSettings step)
    @Published var hasSavedSettings: Bool = false
    
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
    var initialScaleMetricsSnapshot: [String]?
    
    /// Snapshot of scale metrics when Save button was last clicked (for preserving saved changes)
    var savedScaleMetricsSnapshot: [String]?
    
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
    var stepTimerTask: Task<Void, Never>?
    
    let tag = "BtWifiScaleSetupStore"
    let scaleSetupStrings = ScaleSetupStrings.self
    let alertLang = AlertStrings.self
    let commonLang = CommonStrings.self
    let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    let customizeSettingsLang = BtWifiScaleSetupStrings.CustomizeSettingsStrings.self
    
    // Dashboard store used for the Dashboard Metrics customization view
    let dashboardStore = DashboardStore()
    // Snapshots to detect changes and gate Save button enabling
    var initialDisplayNameSnapshot: String?
    var initialScaleModeSnapshot: ScaleModes?
    var initialHeartRateEnabledSnapshot: Bool?
    var initialDashboardMetricLabelsSnapshot: [String]?
    var initialDashboardRemovedMetricsSnapshot: Set<String>?
    var initialDashboardRemovedStreaksSnapshot: Set<String>?
    var initialDashboardStreakOrderSnapshot: [String]?
    var initialDashboardGoalCardRemovedSnapshot: Bool?
    var initialDashboardGoalCardPositionSnapshot: Int?
    var dashboardStoreCancellable: AnyCancellable?
    /// Subscription to dashboard metrics updated notification to sync changes from main dashboard
    var dashboardMetricsUpdatedCancellable: AnyCancellable?
    
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
// swiftlint:disable:next closure_parameter_position
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
// swiftlint:disable:next closure_parameter_position
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
// swiftlint:disable:next closure_parameter_position
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
// swiftlint:disable:next closure_parameter_position
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
                            ScaleMetricsCustomizationView(initialEnabledKeys: selectedScaleMetrics) { [weak self] metrics, _ in
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
                    BtWiFiFinishStepView {
                        self.showAccuCheckInfoModal()
                    }
                )
            }
        }
    }
    
    // MARK: - Lifecycle
    init(
        bluetoothSetupManager: BluetoothSetupManaging = BluetoothSetupManager(),
        wifiSetupManager: WifiSetupManaging = WifiSetupManager(),
        setupCoordinator: ScaleSetupCoordinating = ScaleSetupCoordinator(),
        setupValidationService: SetupValidationServicing = SetupValidationService()
    ) {
        self.bluetoothSetupManager = bluetoothSetupManager
        self.wifiSetupManager = wifiSetupManager
        self.setupCoordinator = setupCoordinator
        self.setupValidationService = setupValidationService

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
            .sink { [weak self] _ in
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
}
