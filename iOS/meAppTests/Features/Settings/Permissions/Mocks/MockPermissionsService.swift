import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockPermissionsService: PermissionsServiceProtocol {
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

    private(set) var setPermissionsCalls = 0
    private(set) var permissionRequestCalls: [GGPermissionType] = []
    private(set) var handlePermissionCalls: [PermissionType] = []
    private(set) var navigateToWifiSettingsCalls = 0

    var permissionRequestResults: [GGPermissionType: GGPermissionState] = [:]
    var handlePermissionResults: [PermissionType: GGPermissionState] = [:]

    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        setPermissionsCalls += 1
        self.permissions = permissions
        permissionsSubject.send(permissions)
    }

    @discardableResult
    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        permissionRequestCalls.append(type)
        let result = permissionRequestResults[type] ?? .DISABLED
        permissions?[type] = result
        permissionsSubject.send(permissions)
        return result
    }

    @discardableResult
    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        handlePermissionCalls.append(type)
        let result = handlePermissionResults[type] ?? .DISABLED
        var current = permissions ?? [:]
        switch type {
        case .bluetooth:
            current[.BLUETOOTH] = result
        case .bluetoothSwitch:
            current[.BLUETOOTH_SWITCH] = result
        case .location:
            current[.LOCATION] = result
        case .locationSwitch:
            current[.LOCATION_SWITCH] = result
        case .camera:
            current[.CAMERA] = result
        case .notification:
            current[.NOTIFICATION] = result
        case .wifiSwitch:
            current[.WIFI_SWITCH] = result
        case .internet:
            break
        }
        permissions = current
        permissionsSubject.send(current)
        return result
    }

    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        permissions?[type]
    }

    func navigateToWifiSettings() {
        navigateToWifiSettingsCalls += 1
    }

    func emitRequiredCategories(_ categories: Set<PermissionCategory>) {
        requiredCategories = categories
        requiredCategoriesSubject.send(categories)
    }

    func emitPermissions(_ permissions: [GGPermissionType: GGPermissionState]?) {
        self.permissions = permissions
        permissionsSubject.send(permissions)
    }
}
