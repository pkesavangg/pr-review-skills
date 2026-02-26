import Combine
import Foundation
import SwiftUI

@MainActor
extension BtWifiScaleSetupStore {
    // MARK: - Bluetooth Permissions

    func bluetoothPermissionStates() -> (isBluetoothEnabled: Bool, isBluetoothSwitchEnabled: Bool) {
        (
            permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED,
            permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        )
    }

    func requestNextMissingBluetoothPermission(
        isBluetoothEnabled: Bool,
        isBluetoothSwitchEnabled: Bool
    ) {
        if !isBluetoothEnabled {
            Task { await permissionsService.handlePermission(.bluetooth) }
        } else if !isBluetoothSwitchEnabled {
            Task { await permissionsService.handlePermission(.bluetoothSwitch) }
        }
    }

    /// Evaluates whether the required permissions have already been granted.
    func arePermissionsEnabled() -> Bool {
        let states = bluetoothPermissionStates()
        return states.isBluetoothEnabled &&
            states.isBluetoothSwitchEnabled &&
            networkMonitor.isConnected
    }

    /// Checks if all required permissions are available
    func hasAllBtPermissions() -> Bool {
        let states = bluetoothPermissionStates()
        return states.isBluetoothEnabled && states.isBluetoothSwitchEnabled
    }

    // MARK: - Form Helpers

    /// Resets form state to pristine/untouched
    func resetFormState() {
        userNameForm.displayName.markAsPristine()
        userNameForm.displayName.markAsUntouched()
    }

    // MARK: - Timeout & Cancellation

    /// Starts timeout for network scan to prevent hanging
    func startNetworkScanTimeout() {
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
    func cancelNetworkScanTimeout() {
        stepTimerTask?.cancel()
        stepTimerTask = nil
    }

    /// Cancels measurement subscription and timeout task
    func cancelMeasurementSubscription() {
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
        liveMeasurementSubscription?.cancel()
        liveMeasurementSubscription = nil
        measurementTimeoutTask?.cancel()
        measurementTimeoutTask = nil
    }

    func cancelStepOnTimeout() {
        stepOnTimeoutTask?.cancel()
        stepOnTimeoutTask = nil
        // Reset skipCheckNetwork when leaving stepOn screen
        HTTPClient.shared.skipCheckNetwork = false
    }

    // MARK: - Navigation

    func navigateToStep(_ step: BtWifiScaleSetupStep, delay: TimeInterval = 0) {
        // Don't navigate if we're exiting, especially from stepOn
        guard !isExiting && !isExitingFromStepOn else { return }

        if currentStep == .gatheringNetwork && step != .gatheringNetwork {
            cancelNetworkScanTimeout()
        }
        if let stepIndex = setupCoordinator.index(for: step, in: steps) {
            currentStepIndex = stepIndex
        }
    }

    /// Returns an adjusted step index by skipping the permissions page when the
    /// permission requirements are already fulfilled.
    /// - Parameters:
    ///   - index: The candidate index to navigate to.
    ///   - direction: `+1` when moving forward; `-1` when moving backwards.
    /// - Returns: A new index that omits the permissions page if it can be skipped.
    func adjustedIndex(from index: Int, direction: Int) -> Int {
        setupCoordinator.adjustedIndex(
            from: index,
            direction: direction,
            steps: steps,
            canSkipPermissions: arePermissionsEnabled()
        )
    }

    // MARK: - Validation

    /// Updates `isNextEnabled` depending on the current step and permission state.
    func updateNextEnabled() {
        let states = bluetoothPermissionStates()
        let hasScaleMetricsChanged = savedScaleMetricsSnapshot != nil && savedScaleMetricsSnapshot != selectedScaleMetrics

        let context = SetupValidationContext(
            currentStep: currentStep,
            scaleSetupError: scaleSetupError,
            currentCustomizeSetting: currentCustomizeSetting,
            networkMonitorConnected: networkMonitor.isConnected,
            bluetoothEnabled: states.isBluetoothEnabled,
            bluetoothSwitchEnabled: states.isBluetoothSwitchEnabled,
            duplicateNameCurrent: removeWhiteSpace(userNameForm.displayName.value),
            duplicateNameInitial: removeWhiteSpace(initialDisplayNameSnapshot ?? (firstName ?? "User")),
            duplicateNameIsValid: userNameForm.displayName.isValid,
            isWifiPasswordValid: wifiSetupManager.isPasswordValid(networkForm: networkForm),
            selectedScaleMode: selectedScaleMode,
            initialScaleModeSnapshot: initialScaleModeSnapshot,
            isHeartRateEnabled: isHeartRateEnabled,
            initialHeartRateEnabledSnapshot: initialHeartRateEnabledSnapshot,
            hasScaleMetricsChanged: hasScaleMetricsChanged,
            hasDashboardCustomizationChanged: hasDashboardCustomizationChanged()
        )

        let result = setupValidationService.evaluate(context: context)
        isNextEnabled = result.isNextEnabled

        if currentStep == .permissions {
            requestNextMissingBluetoothPermission(
                isBluetoothEnabled: states.isBluetoothEnabled,
                isBluetoothSwitchEnabled: states.isBluetoothSwitchEnabled
            )
        }
    }

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
