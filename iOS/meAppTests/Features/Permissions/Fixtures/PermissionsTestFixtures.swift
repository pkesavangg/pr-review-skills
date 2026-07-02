import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

enum PermissionsTestFixtures {
    static func permissionMap(
        bluetooth: GGPermissionState = .ENABLED,
        bluetoothSwitch: GGPermissionState = .ENABLED,
        location: GGPermissionState = .DISABLED,
        locationSwitch: GGPermissionState = .DISABLED,
        camera: GGPermissionState = .DISABLED,
        notification: GGPermissionState = .DISABLED,
        wifiSwitch: GGPermissionState = .DISABLED
    ) -> [GGPermissionType: GGPermissionState] {
        [
            .BLUETOOTH: bluetooth,
            .BLUETOOTH_SWITCH: bluetoothSwitch,
            .LOCATION: location,
            .LOCATION_SWITCH: locationSwitch,
            .CAMERA: camera,
            .NOTIFICATION: notification,
            .WIFI_SWITCH: wifiSwitch
        ]
    }

    static func makeDevice(
        id: String = UUID().uuidString,
        scaleType: DeviceSourceType
    ) -> Device {
        Device(
            id: id,
            accountId: "101",
            deviceName: "Scale-\(id)",
            broadcastIdString: "BID-\(id)",
            bathScale: BathScale(scaleType: scaleType.rawValue, bodyComp: true)
        )
    }
}
