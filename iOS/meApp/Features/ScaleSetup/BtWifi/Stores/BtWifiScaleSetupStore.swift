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
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    /// Active subscription to the Bluetooth discovery publisher – only used during the *wake-up* step.
    private var deviceDiscoveryCancellable: AnyCancellable? = nil
    
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
    
    /// All steps in the setup flow. Exposed as read-only so views can iterate.
    @Published private(set) var steps: [BtWifiScaleSetupStep] = BtWifiScaleSetupStep.allCases
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    let stepsToHideFooter: Set<BtWifiScaleSetupStep> = [
        .wakeup,
        .connectingBluetooth,
        .gatheringNetwork,
        .connectingWifi,
        .stepOn,
        .measurement,
        .scaleConnected
    ]
    
    /// Task handling time-based transitions during testing.
    private var stepTimerTask: Task<Void, Never>? = nil
    
    private let tag = "BtWifiScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    private let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    
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
                        onTryAgain: { [weak self] in self?.retryPairing() },
                        onSupport: {
                            [weak self] in self?.showHelpModal()
                        }
                    )
                )
            default:
                // For now, other screens show the step name as text
                return AnyView(
                    VStack {
                        Text(stepName(for: step))
                            .font(.largeTitle)
                            .padding()
                        Text("Step: \(step.rawValue)")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                )
            }
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
            }
            .store(in: &cancellables)
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
    ///   - sku: The model/SKU (e.g. "0412").
    ///   - discoveredScale: The scale object discovered by Bluetooth (optional).
    ///   - discoveryEvent: The raw discovery event emitted by `BluetoothService` (optional).
    func configure(with sku: String,
                   discoveredScale: Device? = nil,
                   discoveryEvent: DeviceDiscoveryEvent? = nil) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        
        // Reset pairing/discovery state
        resetDiscoveryState()
        // Inject discovery context if provided.
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        
        // Set the starting step (defaults to intro, but may be permissions or connectingBluetooth for direct flow)
        let startStep: BtWifiScaleSetupStep = {
            if discoveredScale != nil && discoveryEvent != nil {
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
    
    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        /// For the last step, simply dismiss the setup.
        if currentStep == .scaleConnected {
            dismissAction?()
            return
        }
        
        let alertLang = AlertStrings.ExitSetupAlert.self
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
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
        return !stepsToHideFooter.contains(currentStep)
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
    
    /// Invoked from the *Try Again* button of `BluetoothConnectionView`.
    private func retryPairing() {
        // Jump back to wake-up step
        if let wakeUpIndex = steps.firstIndex(of: .wakeup) {
            currentStepIndex = wakeUpIndex
        }
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
                    self.connectionState = .success
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        self.moveToNextStep()
                    }
                }
            }
        default:
            break
        }
    }
    
    /// Handles permission changes during the setup flow
    private func handlePermissionChange() {
        let showError = !hasAllBtPermissions()
        if showError {
            if currentStep == .wakeup {
                // Reset discovery state and navigate back to permissions screen
                resetDiscoveryState()
                if let permissionStepIndex = steps.firstIndex(of: .permissions) {
                    currentStepIndex = permissionStepIndex
                }
            }
            // TODO: Handle wifi error screen and measurement error screen later
            // if currentStep == .gatheringNetwork {
            //     gotoWifiErrorScreen()
            // }
            // if currentStep == .stepOn && scaleSetupError != .updateSettingsFailed {
            //     gotoMeasurementErrorScreen()
            // }
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
    }
    
    // MARK: - Scale Pairing
    /// Confirms the pairing with the discovered scale.
    /// TODO: Implement the actual pairing functionality later.
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
        
        // Use cached display name
        let displayName = self.firstName ?? "User"
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
                // Move to next step after a short delay
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    self.moveToNextStep()
                }
                break
            case .duplicateUserError:
                LoggerService.shared.log(level: .error, tag: tag, message: "Duplicate User Error \(response)")
                break
            case .memoryFull:
                LoggerService.shared.log(level: .error, tag: tag, message: "Memory Full \(response)")
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
    
    /// Saves the discovered scale to persistent storage, similar to the TypeScript saveScale method.
    private func saveScale() async {
        guard let discoveryEvent = discoveryEvent,
              let scale = discoveredScale,
              let scaleToken = self.scaleToken else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveScale - missing required data")
            return
        }
        
        do {
            // Create unique scale ID using timestamp (similar to Date.now().toString() in TypeScript)
            let scaleID = String(DateTimeTools.getCurrentTimestampMillis())
            let displayName = self.firstName ?? "User"
            
            // Set up the scale object similar to TypeScript version
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
                ))
            }
            // Update preference properties
            scale.r4ScalePreference?.id = scaleID
            scale.r4ScalePreference?.isSynced = false
            
            // Save the scale using BluetoothService
            try await accountService.updateDashboardType(type: .dashboard12)
            
            
            let result = await bluetoothService.addNewDevice(scale, metaData: nil)
            switch result {
            case .success(let savedScale):
                LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully: \(savedScale.id)")
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
    
    // MARK: - Helper Methods
    // TODO: Need to remove after all steps are implemented
    private func stepName(for step: BtWifiScaleSetupStep) -> String {
        switch step {
        case .intro:
            return "Intro"
        case .permissions:
            return "Permissions"
        case .wakeup:
            return "Wake Up"
        case .connectingBluetooth:
            return "Connecting Bluetooth"
        case .gatheringNetwork:
            return "Gathering Network"
        case .duplicatesFound:
            return "Duplicates Found"
        case .availableWifiList:
            return "Available Wi-Fi List"
        case .wifiPassword:
            return "Wi-Fi Password"
        case .connectingWifi:
            return "Connecting Wi-Fi"
        case .customizeSettings:
            return "Customize Settings"
        case .viewSettings:
            return "View Settings"
        case .updateSettings:
            return "Update Settings"
        case .stepOn:
            return "Step On"
        case .measurement:
            return "Measurement"
        case .scaleConnected:
            return "Scale Connected"
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
        guard currentStep == .permissions else {
            isNextEnabled = true
            return
        }
        
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
    
    deinit {
        // Cancel active Combine subscription before releasing it.
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        
        // Nil out discovery data so subsequent runs start fresh.
        discoveredScale = nil
        discoveryEvent = nil
        
        // Cancel any in-flight timeout task.
        stepTimerTask?.cancel()
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
