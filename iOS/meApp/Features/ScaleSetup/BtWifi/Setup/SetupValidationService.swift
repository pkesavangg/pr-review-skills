import Foundation

struct SetupValidationContext {
    let currentStep: BtWifiScaleSetupStep
    let scaleSetupError: BtWifiScaleSetupError
    let currentCustomizeSetting: CustomizeSettings
    let networkMonitorConnected: Bool
    let bluetoothEnabled: Bool
    let bluetoothSwitchEnabled: Bool
    let duplicateNameCurrent: String
    let duplicateNameInitial: String
    let duplicateNameIsValid: Bool
    let isWifiPasswordValid: Bool
    let selectedDeviceMode: DeviceModes
    let initialDeviceModeSnapshot: DeviceModes?
    let isHeartRateEnabled: Bool
    let initialHeartRateEnabledSnapshot: Bool?
    let hasDeviceMetricsChanged: Bool
    let hasDashboardCustomizationChanged: Bool
}

struct SetupValidationResult {
    let isNextEnabled: Bool
    let shouldRequestBluetoothPermission: Bool
    let shouldRequestBluetoothSwitchPermission: Bool
}

protocol SetupValidationServicing {
    func evaluate(context: SetupValidationContext) -> SetupValidationResult
}

struct SetupValidationService: SetupValidationServicing {
    func evaluate(context: SetupValidationContext) -> SetupValidationResult {
        switch context.currentStep {
        case .permissions:
            return permissionsResult(for: context)
        case .gatheringNetwork:
            return gatheringNetworkResult(for: context)
        case .wifiPassword:
            return validationResult(isNextEnabled: context.isWifiPasswordValid)
        case .viewSettings:
            return viewSettingsResult(for: context)
        default:
            return validationResult(isNextEnabled: true)
        }
    }

    private func permissionsResult(for context: SetupValidationContext) -> SetupValidationResult {
        let isBluetoothEnabled = context.bluetoothEnabled
        let isSwitchEnabled = context.bluetoothSwitchEnabled
        let isNetworkConnected = context.networkMonitorConnected
        return validationResult(
            isNextEnabled: isBluetoothEnabled && isSwitchEnabled && isNetworkConnected,
            shouldRequestBluetoothPermission: !isBluetoothEnabled,
            shouldRequestBluetoothSwitchPermission: isBluetoothEnabled && !isSwitchEnabled
        )
    }

    private func gatheringNetworkResult(for context: SetupValidationContext) -> SetupValidationResult {
        guard context.scaleSetupError == .duplicatesFound else {
            return validationResult(isNextEnabled: true)
        }
        return validationResult(
            isNextEnabled: isDuplicateNameValidAndChanged(in: context)
        )
    }

    private func viewSettingsResult(for context: SetupValidationContext) -> SetupValidationResult {
        let isEnabled: Bool
        switch context.currentCustomizeSetting {
        case .scaleUsername:
            isEnabled = isDuplicateNameValidAndChanged(in: context)
        case .scaleMetrics:
            isEnabled = context.hasDeviceMetricsChanged
        case .scaleMode:
            isEnabled = context.selectedDeviceMode != context.initialDeviceModeSnapshot ||
                context.isHeartRateEnabled != (context.initialHeartRateEnabledSnapshot ?? false)
        case .dashboardMetrics:
            isEnabled = context.hasDashboardCustomizationChanged
        default:
            isEnabled = true
        }
        return validationResult(isNextEnabled: isEnabled)
    }

    private func isDuplicateNameValidAndChanged(in context: SetupValidationContext) -> Bool {
        context.duplicateNameIsValid && context.duplicateNameCurrent != context.duplicateNameInitial
    }

    private func validationResult(
        isNextEnabled: Bool,
        shouldRequestBluetoothPermission: Bool = false,
        shouldRequestBluetoothSwitchPermission: Bool = false
    ) -> SetupValidationResult {
        SetupValidationResult(
            isNextEnabled: isNextEnabled,
            shouldRequestBluetoothPermission: shouldRequestBluetoothPermission,
            shouldRequestBluetoothSwitchPermission: shouldRequestBluetoothSwitchPermission
        )
    }
}
