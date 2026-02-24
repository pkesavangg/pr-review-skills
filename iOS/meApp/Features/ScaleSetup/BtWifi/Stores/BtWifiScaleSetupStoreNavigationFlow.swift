import Combine
import Foundation
import SwiftUI

// swiftlint:disable file_length type_body_length cyclomatic_complexity function_body_length

@MainActor
extension BtWifiScaleSetupStore {
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

    func moveToNextStep() {
        // Don't navigate if we're exiting, especially from stepOn
        guard !isExiting && !isExitingFromStepOn else { return }

        let nextIndex = setupCoordinator.adjustedIndex(
            from: currentStepIndex + 1,
            direction: 1,
            steps: steps,
            canSkipPermissions: arePermissionsEnabled()
        )
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
        
        let previousIndex = setupCoordinator.adjustedIndex(
            from: currentStepIndex - 1,
            direction: -1,
            steps: steps,
            canSkipPermissions: arePermissionsEnabled()
        )
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
// swiftlint:disable:next line_length
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
        self.hasSavedSettings = false
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
    func performExitCleanup() {
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
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
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
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)
            self?.cleanup()
        }
    }
    
    /// Presents the standard exit-alert.
    /// - Parameters:
    ///   - onConfirm: called when user taps **Exit**
    ///   - onCancel:  called when user taps **Go Back**
    func presentExitAlert(onConfirm: @escaping () -> Void,
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
                AlertButtonModel(title: lang.exitButton, type: .primary) { _ in onConfirm() },
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
            Task { @MainActor [weak self] in
                self?.performExitCleanup()
            }
            return
        }
        
        // Set exiting flag first to prevent any navigation during exit
        isExiting = true
        
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
        presentExitAlert(
            onConfirm: { [weak self] in
                self?.performExitCleanup()
            },
            onCancel: { [weak self] in
                guard let self else { return }
                // Cancel exit and stay on current screen
                self.isExiting = false
            }
        )
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
    func handleSettingsWifiSetupExit() -> Bool {
        guard isSettingsWifiSetup && currentStep == .availableWifiList else {
            return false
        }
        
        presentExitAlert(
            onConfirm: { [weak self] in
                self?.cancelWifi()
                self?.scaleSetupError = .none
                // Dismiss sheet first, then clear states after delay
                self?.dismissAction?()
                Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
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
    func showBluetoothTurnedOffAlert() {
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
                    let states = self.bluetoothPermissionStates()
                    self.requestNextMissingBluetoothPermission(
                        isBluetoothEnabled: states.isBluetoothEnabled,
                        isBluetoothSwitchEnabled: states.isBluetoothSwitchEnabled
                    )
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
// swiftlint:disable:next line_length
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
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 100_000_000)
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
            persistDashboardMetricsIfNeeded()
            
            // Navigate to updateSettings if any settings have been saved
            if hasSavedSettings {
                navigateToStep(.updateSettings)
            } else {
                // Skip updateSettings and go directly to stepOn if no settings were saved
                navigateToStep(.stepOn)
            }
        case .scaleConnected:
            // Post notification to refresh dashboard when setup completes
            // Add a small delay to ensure connection status updates have propagated to UI
            // This prevents flicker where scales show as "not connected" briefly
            Task { @MainActor in
                NotificationCenter.default.post(name: .dashboardMetricsUpdated, object: nil)
                // Small delay to allow connection status updates to complete and propagate to UI
                Task { @MainActor in
                try? await Task.sleep(nanoseconds: 500_000_000)
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

}
// swiftlint:enable file_length type_body_length cyclomatic_complexity function_body_length
