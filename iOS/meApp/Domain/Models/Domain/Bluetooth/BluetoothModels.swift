import Foundation
import GGBluetoothSwiftPackage

// MARK: - Bluetooth Wrapper Models
// These lightweight, `Sendable` value types are used by `BluetoothServiceProtocol` so that the
// protocol (and call-sites across the app) remain decoupled from the underlying
// `GGBluetoothSwiftPackage` SDK models.
//
// Concrete service implementations are responsible for mapping between these wrappers and
// the SDK equivalents.
//
// Key design principles:
// - All models are `Sendable` for safe concurrent usage
// - Models provide conversion methods from/to SDK types
// - No SDK types are exposed in public interfaces
// - Models are immutable value types where possible

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
    public var id = UUID()
    public var macAddress: String
    public var ssid: String?
    public var rssi: Int?
    public var password: String?

    public init(macAddress: String, ssid: String? = nil, rssi: Int? = nil, password: String? = nil) {
        self.macAddress = macAddress
        self.ssid = ssid
        self.rssi = rssi
        self.password = password
    }
}

public struct WifiSetupResponse: Sendable, Equatable {
    public var wifiState: String
    public var errorCode: String?

    public init(wifiState: String, errorCode: String? = nil) {
        self.wifiState = wifiState
        self.errorCode = errorCode
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

  public func toGGBTSettingValue() -> GGBTSettingValue {
        switch self {
        case .bool(let boolValue): return .bool(boolValue)
        case .int(let intValue): return .int(intValue)
        case .string(let stringValue): return .string(stringValue)
        }
    }
}

/// Enumeration describing which data set should be cleared on the scale.
public enum DeviceClearType: String, Sendable, CaseIterable {
    case userData     = "USER_DATA"
    case history      = "HISTORY"
    case wifi         = "WIFI"
    case settings     = "SETTINGS"
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
public struct DeviceUser: Sendable, Equatable, Hashable {
    public var name: String
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

/// Wrapper for device information from the Bluetooth SDK
public struct DeviceInfo: Sendable, Equatable, Codable {
    public var manufacturerName: String?
    public var modelNumber: String?
    public var serialNumber: String?
    public var firmwareRevision: String?
    public var hardwareRevision: String?
    public var softwareRevision: String?
    public var systemID: String?
    public var deviceName: String
    public var broadcastId: String?
    public var broadcastIdString: String
    public var password: String?
    public var macAddress: String
    public var wifiMacAddress: String?
    public var identifier: String
    public var protocolType: String?
    public var isWifiConfigured: Bool?
    public var sessionImpedanceSwitchState: Bool?
    public var impedanceSwitchState: Bool?
    public var startAnimationState: Bool?
    public var endAnimationState: Bool?
    public var batteryLevel: Int?
    public var userNumber: Int?
    public var heartRateState: Bool?

    public init(
        manufacturerName: String? = nil,
        modelNumber: String? = nil,
        serialNumber: String? = nil,
        firmwareRevision: String? = nil,
        hardwareRevision: String? = nil,
        softwareRevision: String? = nil,
        systemID: String? = nil,
        deviceName: String,
        broadcastId: String? = nil,
        broadcastIdString: String = "",
        password: String? = nil,
        macAddress: String = "",
        wifiMacAddress: String? = nil,
        identifier: String = "",
        protocolType: String? = nil,
        isWifiConfigured: Bool? = nil,
        sessionImpedanceSwitchState: Bool? = nil,
        impedanceSwitchState: Bool? = nil,
        startAnimationState: Bool? = nil,
        endAnimationState: Bool? = nil,
        batteryLevel: Int? = nil,
        userNumber: Int? = nil,
        heartRateState: Bool? = nil
    ) {
        self.manufacturerName = manufacturerName
        self.modelNumber = modelNumber
        self.serialNumber = serialNumber
        self.firmwareRevision = firmwareRevision
        self.hardwareRevision = hardwareRevision
        self.softwareRevision = softwareRevision
        self.systemID = systemID
        self.deviceName = deviceName
        self.broadcastId = broadcastId
        self.broadcastIdString = broadcastIdString
        self.password = password
        self.macAddress = macAddress
        self.wifiMacAddress = wifiMacAddress
        self.identifier = identifier
        self.protocolType = protocolType
        self.isWifiConfigured = isWifiConfigured
        self.sessionImpedanceSwitchState = sessionImpedanceSwitchState
        self.impedanceSwitchState = impedanceSwitchState
        self.startAnimationState = startAnimationState
        self.endAnimationState = endAnimationState
        self.batteryLevel = batteryLevel
        self.userNumber = userNumber
        self.heartRateState = heartRateState
    }

    /// Conversion from SDK GGDeviceDetails
    public init(sdk: GGDeviceDetails) {
        self.manufacturerName = sdk.manufacturerName
        self.modelNumber = sdk.modelNumber
        self.serialNumber = sdk.serialNumber
        self.firmwareRevision = sdk.firmwareRevision
        self.hardwareRevision = sdk.hardwareRevision
        self.softwareRevision = sdk.softwareRevision
        self.systemID = sdk.systemID
        self.deviceName = sdk.deviceName
        self.broadcastId = sdk.broadcastId
        self.broadcastIdString = sdk.broadcastIdString
        self.password = sdk.password
        self.macAddress = sdk.macAddress
        self.wifiMacAddress = sdk.wifiMacAddress
        self.identifier = sdk.identifier
        self.protocolType = sdk.protocolType
        self.isWifiConfigured = sdk.isWifiConfigured
        self.sessionImpedanceSwitchState = sdk.sessionImpedanceSwitchState
        self.impedanceSwitchState = sdk.impedanceSwitchState
        self.startAnimationState = sdk.startAnimationState
        self.endAnimationState = sdk.endAnimationState
        self.batteryLevel = sdk.batteryLevel
        self.userNumber = sdk.userNumber
        self.heartRateState = sdk.heartRateState
    }
}

/// Wrapper for user creation response types from the Bluetooth SDK
public enum UserCreationResponse: String, Sendable, Codable, Equatable, CaseIterable {
    case creationCompleted = "CREATION_COMPLETED"
    case creationFailed = "CREATION_FAILED"
    case inputDataError = "INPUT_DATA_ERROR"
    case memoryFull = "MEMORY_FULL"
    case duplicateUserError = "DUPLICATE_USER_ERROR"
    case userSelectionInProgress = "USER_SELECTION_IN_PROGRESS"
    case differentUser = "DIFFERENT_USER"
    case notInPairingMode = "NOT_IN_PAIRING_MODE"

    // Conversion from SDK type
    public init(sdkType: UserCreationResponseType) {
        switch sdkType {
        case .CREATION_COMPLETED: self = .creationCompleted
        case .CREATION_FAILED: self = .creationFailed
        case .INPUT_DATA_ERROR: self = .inputDataError
        case .MEMORY_FULL: self = .memoryFull
        case .DUPLICATE_USER_ERROR: self = .duplicateUserError
        case .USER_SELECTION_IN_PROGRESS: self = .userSelectionInProgress
        case .DIFFERENT_USER: self = .differentUser
        case .NOT_IN_PAIRING_MODE: self = .notInPairingMode
        }
    }

    // Conversion to SDK type
    public var sdkType: UserCreationResponseType {
        switch self {
        case .creationCompleted: return .CREATION_COMPLETED
        case .creationFailed: return .CREATION_FAILED
        case .inputDataError: return .INPUT_DATA_ERROR
        case .memoryFull: return .MEMORY_FULL
        case .duplicateUserError: return .DUPLICATE_USER_ERROR
        case .userSelectionInProgress: return .USER_SELECTION_IN_PROGRESS
        case .differentUser: return .DIFFERENT_USER
        case .notInPairingMode: return .NOT_IN_PAIRING_MODE
        }
    }
}

/// Wrapper for user deletion response types from the Bluetooth SDK
public enum UserDeletionResponse: String, Sendable, Codable, Equatable, CaseIterable {
    case success = "SUCCESS"
    case fail = "FAIL"
    case exceptionEncountered = "EXCEPTION_ENCOUNTERED"

    // Conversion from SDK type
    public init(sdkType: UserDeletionResponseType) {
        switch sdkType {
        case .SUCCESS: self = .success
        case .FAIL: self = .fail
        case .EXCEPTION_ENCOUNTERED: self = .exceptionEncountered
        }
    }

    // Conversion to SDK type
    public var sdkType: UserDeletionResponseType {
        switch self {
        case .success: return .SUCCESS
        case .fail: return .FAIL
        case .exceptionEncountered: return .EXCEPTION_ENCOUNTERED
        }
    }
}

/// Scale type enumeration
public enum BluetoothScaleType: String, Sendable, CaseIterable {
    case bluetooth
    case bluetoothScale
    case lcbt
    case lcbtScale
    case btWifiR4
}

/// BPM device type enumeration
public enum BluetoothBpmType: String, Sendable, CaseIterable {
    case bpm
}

/// Represents a blood pressure measurement received from a BPM device.
public struct BpmMeasurement: Sendable, Equatable {
    public let systolic: Int
    public let diastolic: Int
    public let pulse: Int
    public let meanArterial: String?
    public let irregularHb: Bool
    public let timestamp: Date
    public let broadcastId: String?

    public init(
        systolic: Int,
        diastolic: Int,
        pulse: Int,
        meanArterial: String? = nil,
        irregularHb: Bool = false,
        timestamp: Date = Date(),
        broadcastId: String? = nil
    ) {
        self.systolic = systolic
        self.diastolic = diastolic
        self.pulse = pulse
        self.meanArterial = meanArterial
        self.irregularHb = irregularHb
        self.timestamp = timestamp
        self.broadcastId = broadcastId
    }
}

/// Category of device discovered via Bluetooth.
public enum DeviceCategory: String, Sendable, Equatable {
    case scale
    case bpm
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
/// NOTE: `@unchecked Sendable` because `Device` is `@Model` (not thread-safe).
/// This is safe only because all creation (BluetoothService) and consumption
/// (stores/ViewModels) happen on `@MainActor`. Do not send across actor boundaries.
public struct DeviceDiscoveryEvent: @unchecked Sendable, Equatable {
    let device: Device
    let deviceInfo: ScaleItemInfo
    let protocolType: ProtocolType
    let isNew: Bool
    let deviceCategory: DeviceCategory

    init(
        device: Device,
        deviceInfo: ScaleItemInfo,
        protocolType: ProtocolType,
        isNew: Bool,
        deviceCategory: DeviceCategory = .scale
    ) {
        self.device = device
        self.deviceInfo = deviceInfo
        self.protocolType = protocolType
        self.isNew = isNew
        self.deviceCategory = deviceCategory
    }
}

/// Represents firmware update status
public struct FirmwareUpdateStatus: Sendable, Equatable {
    public let progress: Double
    public let isComplete: Bool
    public let error: String?

    public init(progress: Double, isComplete: Bool = false, error: String? = nil) {
        self.progress = progress
        self.isComplete = isComplete
        self.error = error
    }
}

/// Represents a collection of device logs
public struct DeviceLogs {
    /// Array of individual log entries
    let logs: [DeviceLogEntry]
}

/// Represents a single device log entry
public struct DeviceLogEntry {
    /// The MAC address of the device that generated the log
    let macAddress: String?
    /// The log content
    let log: String?
}
