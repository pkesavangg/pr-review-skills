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
    let selectedScaleMode: ScaleModes
    let initialScaleModeSnapshot: ScaleModes?
    let isHeartRateEnabled: Bool
    let initialHeartRateEnabledSnapshot: Bool?
    let hasScaleMetricsChanged: Bool
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
            return SetupValidationResult(
                isNextEnabled: context.bluetoothEnabled && context.bluetoothSwitchEnabled && context.networkMonitorConnected,
                shouldRequestBluetoothPermission: !context.bluetoothEnabled,
                shouldRequestBluetoothSwitchPermission: context.bluetoothEnabled && !context.bluetoothSwitchEnabled
            )
        case .gatheringNetwork:
            if context.scaleSetupError == .duplicatesFound {
                return SetupValidationResult(
                    isNextEnabled: context.duplicateNameIsValid &&
                        context.duplicateNameCurrent != context.duplicateNameInitial,
                    shouldRequestBluetoothPermission: false,
                    shouldRequestBluetoothSwitchPermission: false
                )
            }
            return SetupValidationResult(
                isNextEnabled: true,
                shouldRequestBluetoothPermission: false,
                shouldRequestBluetoothSwitchPermission: false
            )
        case .wifiPassword:
            return SetupValidationResult(
                isNextEnabled: context.isWifiPasswordValid,
                shouldRequestBluetoothPermission: false,
                shouldRequestBluetoothSwitchPermission: false
            )
        case .viewSettings:
            let isEnabled: Bool
            switch context.currentCustomizeSetting {
            case .scaleUsername:
                isEnabled = context.duplicateNameIsValid &&
                    context.duplicateNameCurrent != context.duplicateNameInitial
            case .scaleMetrics:
                isEnabled = context.hasScaleMetricsChanged
            case .scaleMode:
                isEnabled = context.selectedScaleMode != context.initialScaleModeSnapshot ||
                    context.isHeartRateEnabled != (context.initialHeartRateEnabledSnapshot ?? false)
            case .dashboardMetrics:
                isEnabled = context.hasDashboardCustomizationChanged
            default:
                isEnabled = true
            }
            return SetupValidationResult(
                isNextEnabled: isEnabled,
                shouldRequestBluetoothPermission: false,
                shouldRequestBluetoothSwitchPermission: false
            )
        default:
            return SetupValidationResult(
                isNextEnabled: true,
                shouldRequestBluetoothPermission: false,
                shouldRequestBluetoothSwitchPermission: false
            )
        }
    }
}
