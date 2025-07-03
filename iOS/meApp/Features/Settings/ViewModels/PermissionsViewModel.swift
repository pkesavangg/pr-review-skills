import SwiftUI

// MARK: - PermissionsViewModel
/// Holds the current on/off state for each app permission. For now the values are hard-coded; real permission checks will be wired in later.
@MainActor
class PermissionsViewModel: ObservableObject {
    // MARK: Published Permission Flags
    @Published var bluetoothAuthorized: Bool = true
    @Published var bluetoothPoweredOn: Bool = true
    @Published var locationServicesEnabled: Bool = true
    @Published var locationAuthorized: Bool = false
    @Published var cameraAuthorized: Bool = true
    @Published var notificationsEnabled: Bool = true
    @Published var internetConnected: Bool = false
    // Holds the currently connected Wi-Fi SSID (nil if not connected)
    @Published var wifiNetworkName: String? = nil
    
    // TODO: IT should be replaced with the GGBluetoothSwiftPackage permission types
    enum PermissionType: String {
        case notification = "NOTIFICATION"
        case bluetoothSwitch = "BLUETOOTH_SWITCH"
        case bluetooth = "BLUETOOTH"
        case nearbyDevice = "NEARBY_DEVICE"
        case location = "LOCATION"
        case locationSwitch = "LOCATION_SWITCH"
        case camera = "CAMERA"
        case wifiSwitch = "WIFI_SWITCH"
        case internet = "INTERNET"
    }
    
    func handlePermission(_ type: PermissionType) {
        // TODO: Implement permission handling logic for the specific permission type
        print("Handle permission: \(type.rawValue)")
    }
}
