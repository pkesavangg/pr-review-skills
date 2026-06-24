import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

enum PermissionsStoreTestFixtures {
    static let bluetoothEnabled: [GGPermissionType: GGPermissionState] = [
        .BLUETOOTH: .ENABLED,
        .BLUETOOTH_SWITCH: .ENABLED
    ]

    static let bluetoothDisabled: [GGPermissionType: GGPermissionState] = [
        .BLUETOOTH: .DISABLED,
        .BLUETOOTH_SWITCH: .DISABLED
    ]
}
