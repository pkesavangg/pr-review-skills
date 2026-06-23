import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockSettingsPermissionsService: PermissionsServiceProtocol {

    // MARK: - State

    var permissions: [GGPermissionType: GGPermissionState]?
    var requiredCategories: Set<PermissionCategory> = []

    private let permissionsSubject = CurrentValueSubject<[GGPermissionType: GGPermissionState]?, Never>(nil)
    private let requiredCategoriesSubject = CurrentValueSubject<Set<PermissionCategory>, Never>([])

    var permissionsPublisher: AnyPublisher<[GGPermissionType: GGPermissionState]?, Never> {
        permissionsSubject.eraseToAnyPublisher()
    }

    var requiredCategoriesPublisher: AnyPublisher<Set<PermissionCategory>, Never> {
        requiredCategoriesSubject.eraseToAnyPublisher()
    }

    // MARK: - Call tracking

    var handlePermissionCallCount = 0
    var lastHandledPermission: PermissionType?
    var handlePermissionResult: GGPermissionState = .ENABLED

    // MARK: - PermissionsServiceProtocol

    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        self.permissions = permissions
        permissionsSubject.send(permissions)
    }

    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        .ENABLED
    }

    @discardableResult
    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        handlePermissionCallCount += 1
        lastHandledPermission = type
        let ggType: GGPermissionType = type == .bluetooth ? .BLUETOOTH : .BLUETOOTH_SWITCH
        let result = handlePermissionResult
        var updated = permissions ?? [:]
        updated[ggType] = result
        setPermissions(updated)
        return result
    }

    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        permissions?[type]
    }

    // MARK: - Helpers

    func sendRequiredCategories(_ categories: Set<PermissionCategory>) {
        requiredCategories = categories
        requiredCategoriesSubject.send(categories)
    }

    func grantBluetooth() {
        var updated = permissions ?? [:]
        updated[.BLUETOOTH] = .ENABLED
        updated[.BLUETOOTH_SWITCH] = .ENABLED
        setPermissions(updated)
    }

    func revokeBluetooth() {
        var updated = permissions ?? [:]
        updated[.BLUETOOTH] = .DISABLED
        updated[.BLUETOOTH_SWITCH] = .DISABLED
        setPermissions(updated)
    }

    func reset() {
        permissions = nil
        requiredCategories = []
        permissionsSubject.send(nil)
        requiredCategoriesSubject.send([])
        handlePermissionCallCount = 0
        lastHandledPermission = nil
    }
}
