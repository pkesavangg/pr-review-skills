import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockPermissionsService: PermissionsServiceProtocol {
    @Published var permissions: [GGPermissionType: GGPermissionState]?
    var permissionsPublisher: AnyPublisher<[GGPermissionType: GGPermissionState]?, Never> { $permissions.eraseToAnyPublisher() }
    var requiredCategories: Set<PermissionCategory> = []

    var permissionRequestResult: GGPermissionState = .ENABLED
    var handlePermissionResult: GGPermissionState = .ENABLED

    private(set) var permissionRequestCalls = 0
    private(set) var handlePermissionCalls = 0
    private(set) var lastPermissionRequestType: GGPermissionType?
    private(set) var lastHandledPermission: PermissionType?

    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        self.permissions = permissions
    }

    @discardableResult
    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        permissionRequestCalls += 1
        lastPermissionRequestType = type
        return permissionRequestResult
    }

    @discardableResult
    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        handlePermissionCalls += 1
        lastHandledPermission = type
        return handlePermissionResult
    }

    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        permissions?[type]
    }
}
