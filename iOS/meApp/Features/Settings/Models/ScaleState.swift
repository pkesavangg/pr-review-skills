import SwiftUI
import Foundation

/// Main scale state container
struct ScaleState {
    var ui: ScaleUIState = ScaleUIState()
    var data: ScaleDataState = ScaleDataState()
    var device: ScaleDeviceState = ScaleDeviceState()
    var users: ScaleUsersState = ScaleUsersState()
    var wifi: ScaleWifiState = ScaleWifiState()
    var modes: ScaleModesState = ScaleModesState()
    var metrics: ScaleMetricsState = ScaleMetricsState()
}

// MARK: - UI State
struct ScaleUIState {
    var isLoading: Bool = false
    var errorMessage: String? = nil
    var showErrorToast: ToastModel? = nil
    var showNicknameAlert: Bool = false
    var showTermsBrowser: Bool = false
    var showPassword: Bool = false
    var isWifiLoading: Bool = false
    var isLoadingUsers: Bool = false
    var showWeightOnlyBanner: Bool = false
    var showWeightOnlyInfo: Bool = false
    var showHeartRateBanner: Bool = false
}

// MARK: - Data State
struct ScaleDataState {
    var scales: [Device] = []
    var addScaleForm = AddScaleForm()
    var wifiPasswordValidationForm = WifiPasswordValidationForm()
    var nicknameInput: String = ""
    var browserURL: URL? = nil
}

// MARK: - Device State
struct ScaleDeviceState {
    var scale: Device? = nil
    var firmwareVersion: String? = nil
    var macAddress: String? = nil
    var connectedWifiSSID: String? = nil
    var deviceInfo: DeviceMetaData? = nil
    var isBluetoothScale: Bool = false
    var isDeviceConnected: Bool = false
    var scaleTypeValue: String = "Bluetooth/Wi-Fi"
    var skuValue: String = SettingsConstants.defaultR4Sku
    var modeValue: ScaleModes = .weightOnly
}

// MARK: - Users State
struct ScaleUsersState {
    var deviceUsers: [DeviceUser] = []
    var currentDeviceUser: DeviceUser? = nil
    var isLoadingUsers: Bool = false
}

// MARK: - WiFi State
struct ScaleWifiState {
    var wifiConnectionState: ConnectionState = .loading
    var connectedWifiNetwork: String? = nil
    var wifiNetworks: [String] = ["greatergoods1", "great2542", "ggtesting"]
}

// MARK: - Modes State
struct ScaleModesState {
    var originalModeValue: ScaleModes = .weightOnly
    var isHeartRateEnabled: Bool = false
    var originalHeartRateEnabled: Bool = false
    var hasModeChanges: Bool = false
    var modeValue: ScaleModes = .weightOnly
}

// MARK: - Metrics State
struct ScaleMetricsState {
    var metrics: [ScaleMetricSetting] = ScaleMetrics.bodyMetrics
    var progressMetrics: [ScaleMetricSetting] = ScaleMetrics.progressMetrics
    var displayMetricsValue: String = ""
} 