//
//  A6ScaleSetupStore.swift
//  meApp
//
//  Created by Cursor AI on 08/07/25.
//

import Combine
import Foundation
import SwiftUI
/// Store responsible for orchestrating the A6 (LCBT) scale-setup multi-step flow.
@MainActor
// swiftlint:disable:next type_body_length
final class A6ScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    /// Centralised permission handling service.
    @Injector private var permissionsService: PermissionsService
    /// Bluetooth service for device discovery
    @Injector private var bluetoothService: BluetoothService
    /// Scale service for scale operations
    @Injector private var scaleService: ScaleService
    /// Account service for account operations
    @Injector private var accountService: AccountService
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    /// Active subscription to the Bluetooth discovery publisher – only used during the *wake-up* step.
    private var deviceDiscoveryCancellable: AnyCancellable?
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    private var originalSku: String?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: (() -> Void)?
    /// Discovered scale information
    private var discoveredScale: Device?
    /// Discovery event from Bluetooth service
    private var discoveryEvent: DeviceDiscoveryEvent?
    
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    
    // Observe step changes to trigger the timers.
    @Published private(set) var currentStep: A6ScaleSetupStep = .intro {
        didSet { handleStepChange() }
    }
    // Connection status shown on the BluetoothConnectionView.
    @Published var connectionState: ConnectionState = .loading
    
    /// All steps in the setup flow. Exposed as read-only so views can iterate.
    @Published private(set) var steps: [A6ScaleSetupStep] = A6ScaleSetupStep.allCases
    
    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true
    
    /// Task handling time-based transitions during testing.
    private var stepTimerTask: Task<Void, Never>?
    private let tag = "A6ScaleSetupStore"
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
                return AnyView(PermissionListView(setupType: .bluetooth))
            case .wakeUp:
                return AnyView(ConnectionPromptView(
                    subtitle: scaleSetupStrings.wakeYourScaleSubtitleLCBT,
                    scaleImagePath: scaleItem.imgPath
                ))
            case .connectingBluetooth:
                return AnyView(
                    BluetoothConnectionView(
                        state: connectionState,
                        setupType: .lcbt,
                        onTryAgain: { [weak self] in self?.retryPairing() },
                        onSupport: {
// swiftlint:disable:next closure_parameter_position
                            [weak self] in self?.showHelpModal()
                        }
                    )
                )
            case .setupFinished:
                let lang = scaleSetupStrings.FinishViewStrings.self
                return AnyView(
                    ScaleSetupFinishView(title: lang.title, description: lang.description)
                        .environmentObject(Theme.shared)
                )
            }
        }
    }
    
    // MARK: - Lifecycle
    init() {
        // Observe permission updates so the footer button reacts instantly.
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
                self?.handlePermissionChange()
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
    ///   - sku:            The model/SKU (e.g. "0378").
    ///   - discoveredScale: The scale object discovered by Bluetooth (optional).
    ///   - discoveryEvent:  The raw discovery event emitted by `BluetoothService` (optional).
    ///   - startStep:       The initial step for the wizard (defaults to `.intro`).
    func configure(with sku: String,
                   discoveredScale: Device? = nil,
                   discoveryEvent: DeviceDiscoveryEvent? = nil) {
        self.originalSku = sku
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let resolved = SCALES.first { $0.sku == lookupSku } ?? SCALES.first
        self.scaleItem = resolved
        // Reset pairing/discovery state
        resetDiscoveryState()
        // Inject discovery context if provided.
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        
        // Set setup in progress flag to prevent goal modals during setup
        bluetoothService.isSetupInProgress = true
        
        // Set the starting step (defaults to intro, but may be connectingBluetooth for direct flow)
        let startStep: A6ScaleSetupStep = discoveredScale != nil && discoveryEvent != nil ? .connectingBluetooth : .intro
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
        if currentStep == .setupFinished {
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
                if self.discoveredScale == nil && self.currentStep == .wakeUp {
                    self.moveToNextStep()
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 250_000_000)
                        self.connectionState = .failure
                    }
                }
            }
        }
    }
    
    /// Invoked from the *Try Again* button of `BluetoothConnectionView`.
    private func retryPairing() {
        // Jump back to wake-up step
        if let wakeUpIndex = steps.firstIndex(of: .wakeUp) {
            currentStepIndex = wakeUpIndex
        }
    }
    
    // MARK: - Step Change Handling
    
    private func handleStepChange() {
        switch currentStep {
        case .wakeUp:
            self.pair()
        case .connectingBluetooth:
            self.connectionState = .loading
            Task {
                if discoveredScale != nil && discoveryEvent != nil {
                    await self.saveDiscoveredScale()
                    self.connectionState = .success
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
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
        let showError = !isBluetoothPermissionEnabled()
        if showError {
            if currentStep == .wakeUp {
                // Reset discovery state and navigate back to permissions screen
                resetDiscoveryState()
                if let permissionStepIndex = steps.firstIndex(of: .permissions) {
                    currentStepIndex = permissionStepIndex
                }
            }
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
        // Note: originalSku is NOT reset here - it should persist for the entire setup flow
    }
    
    // MARK: - Scale Saving
    private func saveDiscoveredScale() async {
        guard let discoveryEvent = discoveryEvent, let scale = discoveredScale else { return }
        
        // Use the proper service method to create the A6/LCBT scale with correct relationship handling
        do {
            // Get account ID from account service
            guard let accountId = accountService.activeAccount?.accountId else {
                LoggerService.shared.log(level: .error, tag: tag, message: "No active account found for scale creation")
                return
            }
            
            // This preserves the original SKU (e.g., "0022") passed to the setup flow
            let finalSku = originalSku ?? scaleItem?.sku ?? discoveryEvent.device.sku
            
            let savedScale = try await scaleService.createA6Scale(
                device: scale,
                sku: finalSku,
                accountId: accountId,
                deviceMetadata: nil,
                skipDuplicateCheck: false
            )
            
            await scaleService.syncAllScalesWithRemote()
            bluetoothService.syncDevices([])
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully: \(savedScale.id)")
            
            // Post notification that scale was added
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            
            // Clear setup in progress flag after scale is saved
            bluetoothService.isSetupInProgress = false
            
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to save scale: \(error.localizedDescription)")
            self.notificationService.showToast(ToastModel(message: ToastStrings.saveScaleError))
            // Clear setup in progress flag even on error
            bluetoothService.isSetupInProgress = false
        }
    }
    
    // MARK: - Device Discovery Handling
    private func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        // Only handle discovery during wake-up step
        guard currentStep == .wakeUp else { return }
        // Only handle LCBT/A6 scales
        guard event.deviceInfo.setupType == .lcbt else { return }
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        self.discoveryEvent = event
        self.discoveredScale = event.device
        
        // Check if this is a known scale (isNew = false means it's known)
        if !event.isNew {
            // Add device to skip list and stop scanning to prevent rediscovery loop
            if let broadcastId = event.device.broadcastIdString, !broadcastId.isEmpty {
                Task {
                    // Skip this device to prevent rediscovery loop
                    _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId, considerForSession: false)
                }
            }
            bluetoothService.stopScan()
            showKnownScaleAlert()
        } else {
            // New scale discovered - move to next step
            moveToNextStep()
        }
    }
    
    /// Evaluates whether the required Bluetooth permission has already been granted.
    private func isBluetoothPermissionEnabled() -> Bool {
        // The PermissionService tracks GG SDK permissions. Treat `ENABLED` as satisfied.
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }
    
    /// Updates `isNextEnabled` depending on the current step and permission state.
    private func updateNextEnabled() {
        guard currentStep == .permissions else {
            isNextEnabled = true
            return
        }
        
        // Evaluate individual Bluetooth-related permissions
        let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
        let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        
        // Automatically request the missing permission giving priority to `.bluetooth`
        if !bluetoothEnabled {
            Task { await permissionsService.handlePermission(.bluetooth) }
        } else if !bluetoothSwitchEnabled {
            Task { await permissionsService.handlePermission(.bluetoothSwitch) }
        }
        
        // Enable the Next button only when both permissions are granted
        isNextEnabled = bluetoothEnabled && bluetoothSwitchEnabled
    }
    
    /// Returns an adjusted step index by skipping the *permissions* page when the Bluetooth
    /// permission requirements are already fulfilled.
    /// - Parameters:
    ///   - index: The candidate index to navigate to.
    ///   - direction: `+1` when moving forward; `-1` when moving backwards.
    /// - Returns: A new index that omits the permissions page if it can be skipped.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .permissions,
              isBluetoothPermissionEnabled() {
            idx += direction
        }
        return idx
    }
    
    /// Shows an alert when a known scale is discovered.
    private func showKnownScaleAlert() {
        let alertStrings = AlertStrings.KnownScaleDiscoveredAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.exitButton, type: .primary) { [weak self] _ in
                    guard let self = self else { return }
                    // Perform proper cleanup before dismissing
                    self.cleanUp()
                    self.dismissAction?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Cleans up all subscriptions and resources when the view disappears
    func cleanUp() {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        stepTimerTask = nil
        resetDiscoveryState()
        // Re-apply skipped devices to BLE SDK, excluding paired scales
        bluetoothService.reapplySkipDevicesExcludingPaired()
        // Clear setup in progress flag when setup is dismissed
        bluetoothService.isSetupInProgress = false
    }
    
    // MARK: - A6 Scale Unit Update
    /// Marks A6 scale preferences as unsynced so updated units are applied on reconnect.
    func markA6ScalesUnsyncedForUnitUpdate() async {
        let a6Scales = scaleService.scales.filter { $0.protocolType == "A6" }
        guard !a6Scales.isEmpty else {
            LoggerService.shared.log(level: .debug, tag: tag, message: "No A6 scales found")
            return
        }

        for scale in a6Scales {
            guard let preference = scaleService.fetchAttachedPreferenceSync(by: scale.id) else { continue }

            preference.isSynced = false
            do {
                try await scaleService.updateScalePreference(scale.id, preference)
            } catch {
                LoggerService.shared.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to update A6 scale \(scale.broadcastIdString ?? "unknown"): \(error)"
                )
            }
        }

        // Ensure SDK picks up updated unit on reconnect
        bluetoothService.syncDevices(scaleService.scales)
    }
    // Cancel active Combine subscription before releasing it.
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
