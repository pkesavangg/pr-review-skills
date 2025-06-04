import Foundation

/// Enum representing the state of a permission (enabled, disabled, etc.).
enum PermissionState: String, Codable, Equatable {
    case enabled = "ENABLED"
    case disabled = "DISABLED"
    case notDetermined = "NOT_DETERMINED"
    case notRequested = "NOT_REQUESTED"
    case approxLocation = "APPROX_LOCATION"
}

/// Struct representing the status of all permissions for the app.
struct PermissionStatus: Codable, Equatable {
    /// Notification permission state
    var notification: PermissionState
    /// Nearby device permission state (Android only, but included for parity)
    var nearbyDevice: PermissionState?
    /// Bluetooth switch permission state
    var bluetoothSwitch: PermissionState
    /// Bluetooth permission state (optional)
    var bluetooth: PermissionState?
    /// Location switch permission state
    var locationSwitch: PermissionState
    /// Location permission state
    var location: PermissionState
    /// Camera permission state
    var camera: PermissionState
    /// All permissions state (aggregate)
    var all: PermissionState

    enum CodingKeys: String, CodingKey {
        case notification = "NOTIFICATION"
        case nearbyDevice = "NEARBY_DEVICE"
        case bluetoothSwitch = "BLUETOOTH_SWITCH"
        case bluetooth = "BLUETOOTH"
        case locationSwitch = "LOCATION_SWITCH"
        case location = "LOCATION"
        case camera = "CAMERA"
        case all = "ALL"
    }
}
