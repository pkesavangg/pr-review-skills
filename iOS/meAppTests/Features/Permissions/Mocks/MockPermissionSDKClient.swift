import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockPermissionSDKClient: PermissionSDKClient {
    var requestResults: [GGPermissionType: String] = [:]
    private(set) var requestedPermissionTypes: [GGPermissionType] = []
    private(set) var navigatedPermissionTypes: [GGPermissionType] = []

    func requestPermission(permissionType: GGPermissionType) async -> String {
        requestedPermissionTypes.append(permissionType)
        return requestResults[permissionType] ?? GGPermissionState.DISABLED.rawValue
    }

    func navigateToSettings(permissionType: GGPermissionType) async -> String {
        navigatedPermissionTypes.append(permissionType)
        return GGPermissionState.ENABLED.rawValue
    }
}
