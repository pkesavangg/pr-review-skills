import Foundation

// MARK: - Bluetooth Wrapper Models
// These lightweight, `Sendable` value types are used by `BluetoothServiceProtocol` so that the
// protocol (and call-sites across the app) remain decoupled from the underlying
// `GGBluetoothSwiftPackage` SDK models.
//
// Concrete service implementations are responsible for mapping between these wrappers and
// the SDK equivalents.

/// Minimal representation of a Wi-Fi configuration used during setup.
public struct WifiConfig: Sendable, Equatable {
    public let ssid: String
    public let password: String?


    public init(ssid: String, password: String? = nil) {
        self.ssid = ssid
        self.password = password
    }
}

public struct WifiDetails: Sendable, Equatable {
    public  var macAddress: String
    public  var ssid: String?
    public var rssi: Int?
    public var password: String?

    public init(macAddress: String, ssid: String? = nil, rssi: Int? = nil, password: String? = nil) {
        self.macAddress = macAddress
        self.ssid = ssid
        self.rssi = rssi
        self.password = password
    }
}

/// Represents a key/value device-setting update request.
public struct DeviceSetting: Sendable, Equatable {
    public let key: String
    public let value: DeviceSettingValue
    public init(key: String, value: DeviceSettingValue) {
        self.key = key
        self.value = value
    }
}

public enum DeviceSettingValue: Sendable, Equatable {
  case bool(Bool)
  case int(Int)
  case string(String)
}

/// Enumeration describing which data set should be cleared on the scale.
public enum DeviceClearType: String, Sendable, CaseIterable {
    case userData     = "USER_DATA"
    case history      = "HISTORY"
    case all          = "ALL"
}

/// Placeholder for live measurement data streaming from the scale.
/// Extend as concrete requirements evolve.
public struct MeasurementLiveData: Sendable, Equatable {
    public let weight: Double?
    public let timestamp: Date
    // Add impedance, heart-rate, etc. as needed
    public init(weight: Double?, timestamp: Date = Date()) {
        self.weight = weight
        self.timestamp = timestamp
    }
}

/// Representation of a user slot stored on the scale (R4 only).
public struct DeviceUser: Sendable, Equatable {
    public let name: String
    public let token: String?
    public let lastActive: Int
    public let isBodyMetricsEnabled: Bool
    public init(name: String, token: String? = nil, lastActive: Int = 0, isBodyMetricsEnabled: Bool = false) {
        self.name = name
        self.token = token
        self.lastActive = lastActive
        self.isBodyMetricsEnabled = isBodyMetricsEnabled
    }
}


/// Scale type enumeration
public enum BluetoothScaleType: String, Sendable, CaseIterable {
    case bluetooth = "bluetooth"
    case bluetoothScale = "bluetoothScale"
    case lcbt = "lcbt"
    case lcbtScale = "lcbtScale"
    case btWifiR4 = "btWifiR4"
}


/// Unified device discovery event
///
/// Usage example:
/// ```swift
/// bluetoothService.deviceDiscoveredPublisher
///     .sink { event in
///         switch (event.protocolType, event.isNew) {
///         case (.A6, true):
///             // Handle new A6 scale discovery
///             handleNewA6Scale(event.device)
///         case (.A6, false):
///             // Handle known A6 scale during setup
///             handleKnownA6ScaleDuringSetup(event.device)
///         case (.A3, true):
///             // Handle new A3 scale discovery
///             handleNewA3Scale(event.device)
///         case (.R4, true):
///             // Handle new Smart WiFi scale discovery
///             handleNewSmartWifiScale(event.device)
///         case (.R4, false):
///             // Handle known Smart scale during setup
///             handleKnownSmartScaleDuringSetup(event.device)
///         default:
///             break
///         }
///     }
///     .store(in: &cancellables)
/// ```
public struct DeviceDiscoveryEvent: Sendable, Equatable {
    let device: Device
    let deviceInfo: ScaleItemInfo
    let protocolType: ProtocolType
    let isNew: Bool

  init(device: Device, deviceInfo: ScaleItemInfo, protocolType: ProtocolType, isNew: Bool) {
        self.device = device
        self.deviceInfo = deviceInfo
        self.protocolType = protocolType
        self.isNew = isNew
    }
}


