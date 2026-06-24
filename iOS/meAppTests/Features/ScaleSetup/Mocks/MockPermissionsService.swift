//
//  MockPermissionsService.swift
//  meAppTests
//

import Foundation
import Combine
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockPermissionsService: PermissionsServiceProtocol {

    // MARK: - permissions
    var permissions: [GGPermissionType: GGPermissionState]?

    private let permissionsSubject = CurrentValueSubject<[GGPermissionType: GGPermissionState]?, Never>(nil)
    var permissionsPublisher: AnyPublisher<[GGPermissionType: GGPermissionState]?, Never> {
        permissionsSubject.eraseToAnyPublisher()
    }

    // MARK: - setPermissions
    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        self.permissions = permissions
        permissionsSubject.send(permissions)
    }

    // MARK: - permissionRequest
    var permissionRequestResult: GGPermissionState = .DISABLED

    @discardableResult
    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        permissionRequestResult
    }

    // MARK: - handlePermission
    var handlePermissionResult: GGPermissionState = .DISABLED

    @discardableResult
    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        handlePermissionResult
    }

    // MARK: - getPermissionState
    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        permissions?[type]
    }

    // MARK: - Helpers
    func grantAll() {
        let state: [GGPermissionType: GGPermissionState] = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED,
            .CAMERA: .ENABLED
        ]
        setPermissions(state)
    }

    func revokeAll() {
        let state: [GGPermissionType: GGPermissionState] = [
            .BLUETOOTH: .DISABLED,
            .BLUETOOTH_SWITCH: .DISABLED,
            .CAMERA: .DISABLED
        ]
        setPermissions(state)
    }
}
