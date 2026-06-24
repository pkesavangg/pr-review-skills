import Combine
import Foundation
import Network
import GGBluetoothSwiftPackage
import ggWifiScalePackage
@testable import meApp

@MainActor
final class MockWifiScaleRepositoryAPI: WifiDeviceRepositoryAPIProtocol {
    var getScaleTokenResult: Result<WifiScaleTokenResponse, Error> = .success(WifiScaleTestFixtures.makeTokenResponse())
    private(set) var getScaleTokenCalls = 0
    private(set) var lastRequest: String?

    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        getScaleTokenCalls += 1
        lastRequest = request
        return try getScaleTokenResult.get()
    }
}

@MainActor
final class MockWifiPermissionsService: PermissionsServiceProtocol {
    @Published var permissions: [GGPermissionType: GGPermissionState]?
    var permissionsPublisher: AnyPublisher<[GGPermissionType: GGPermissionState]?, Never> { $permissions.eraseToAnyPublisher() }
    var requiredCategories: Set<PermissionCategory> = []
    private let requiredCategoriesSubject = CurrentValueSubject<Set<PermissionCategory>, Never>([])
    var requiredCategoriesPublisher: AnyPublisher<Set<PermissionCategory>, Never> {
        requiredCategoriesSubject.eraseToAnyPublisher()
    }

    var permissionStates: [GGPermissionType: GGPermissionState] = [:]
    private(set) var navigateToWifiSettingsCalls = 0

    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        self.permissions = permissions
        for (key, value) in permissions {
            permissionStates[key] = value
        }
    }

    func setRequiredCategories(_ categories: Set<PermissionCategory>) {
        requiredCategories = categories
        requiredCategoriesSubject.send(categories)
    }

    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        permissionStates[type] ?? .NOT_REQUESTED
    }

    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        .NOT_REQUESTED
    }

    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        permissionStates[type]
    }

    func navigateToWifiSettings() {
        navigateToWifiSettingsCalls += 1
    }
}

@MainActor
struct MockWifiNetworkStatusProvider: WifiNetworkStatusProviding {
    var isConnected: Bool
    var connectionType: NWInterface.InterfaceType?
}

@MainActor
final class MockWifiInfoProvider: WifiInfoProviding {
    var ssid: String?
    var bssid: String?
    private(set) var currentSSIDCalls = 0
    private(set) var currentBSSIDCalls = 0

    func currentSSID() async -> String? {
        currentSSIDCalls += 1
        return ssid
    }

    func currentBSSID() async -> String? {
        currentBSSIDCalls += 1
        return bssid
    }
}

@MainActor
final class MockWifiScaleSetupClient: WifiScaleSetupClientProtocol {
    var smartConnectResult: Result<Void, Error> = .success(())
    var espSmartConnectResult: Result<Void, Error> = .success(())
    var apModeResult: Result<Void, Error> = .success(())

    private(set) var smartConnectCalls = 0
    private(set) var espSmartConnectCalls = 0
    private(set) var apModeCalls = 0
    private(set) var cancelCalls = 0

    private(set) var lastSmartConfig: WifiScaleConfig?
    private(set) var lastEspConfig: WifiScaleConfig?
    private(set) var lastApConfig: WifiScaleConfig?

    func smartConnect(config: WifiScaleConfig) async throws {
        smartConnectCalls += 1
        lastSmartConfig = config
        try smartConnectResult.get()
    }

    func espSmartConnect(config: WifiScaleConfig) async throws {
        espSmartConnectCalls += 1
        lastEspConfig = config
        try espSmartConnectResult.get()
    }

    func apMode(config: WifiScaleConfig) async throws {
        apModeCalls += 1
        lastApConfig = config
        try apModeResult.get()
    }

    func cancel() {
        cancelCalls += 1
    }
}
