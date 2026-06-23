import Foundation
import SwiftUI

/// Main scale state container
struct DeviceState {
    var ui = DeviceUIState()
    var data = DeviceDataState()
    var device = DeviceDeviceState()
    var users = DeviceUsersState()
    var wifi = DeviceWifiState()
    var modes = DeviceModesState()
    var metrics = DeviceMetricsState()
}

// MARK: - UI State
struct DeviceUIState {
    var showTermsBrowser: Bool = false
    var showWeightOnlyBanner: Bool = false
    var showWeightOnlyInfo: Bool = false
    var showHeartRateBanner: Bool = false
}

// MARK: - Data State
struct DeviceDataState {
    var scales: [Device] = []
    var addScaleForm = AddDeviceForm()
    var wifiPasswordValidationForm = WifiPasswordValidationForm()
    var nicknameInput: String = ""
    var browserURL: URL?
}

// MARK: - Device State
struct DeviceDeviceState {
    var scale: Device?
    var firmwareVersion: String?
    var macAddress: String?
    var connectedWifiSSID: String?
    var deviceInfo: DeviceMetaData?
    var isBluetoothScale: Bool = false
    var isDeviceConnected: Bool = false
    var scaleTypeValue: String = "Bluetooth/Wi-Fi"
    var skuValue: String = SettingsConstants.defaultR4Sku
    var modeValue: DeviceModes = .weightOnly
}

// MARK: - Users State
struct DeviceUsersState {
    var deviceUsers: [DeviceUser] = []
    var currentDeviceUser: DeviceUser?
    var isLoadingUsers: Bool = false
}

// MARK: - WiFi State
struct DeviceWifiState {
    var wifiConnectionState: ConnectionState = .loading
    var connectedWifiNetwork: String?
    var wifiNetworks: [String] = ["greatergoods1", "great2542", "ggtesting"]
}

// MARK: - Modes State
struct DeviceModesState {
    var originalModeValue: DeviceModes = .weightOnly
    var isHeartRateEnabled: Bool = false
    var originalHeartRateEnabled: Bool = false
    var hasModeChanges: Bool = false
    var modeValue: DeviceModes = .weightOnly
}

// MARK: - Metrics State
struct DeviceMetricsState {
    var metrics: [DeviceMetricSetting] = DeviceMetrics.bodyMetrics
    var progressMetrics: [DeviceMetricSetting] = DeviceMetrics.progressMetrics
    var displayMetricsValue: String = ""
} 
