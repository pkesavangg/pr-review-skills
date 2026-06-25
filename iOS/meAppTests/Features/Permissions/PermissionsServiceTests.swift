import Combine
import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct PermissionsServiceTests {
    @Test("setPermissions and getPermissionState: stores and returns cached states")
    func setPermissionsStoresState() {
        let sut = makeSUT()
        let map = PermissionsTestFixtures.permissionMap(
            bluetooth: .ENABLED,
            location: .DISABLED
        )

        sut.setPermissions(map)

        #expect(sut.permissions?[.BLUETOOTH] == .ENABLED)
        #expect(sut.permissions?[.LOCATION] == .DISABLED)
        #expect(sut.getPermissionState(.BLUETOOTH) == .ENABLED)
        #expect(sut.getPermissionState(.LOCATION) == .DISABLED)
    }

    @Test("updatePermission initializes map and merges with existing values")
    func updatePermissionInitializesAndMerges() {
        let sut = makeSUT()
        sut.updatePermission(.BLUETOOTH_SWITCH, to: .ENABLED)
        sut.updatePermission(.LOCATION, to: .DISABLED)

        #expect(sut.permissions?.count == 2)
        #expect(sut.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED)
        #expect(sut.getPermissionState(.LOCATION) == .DISABLED)
    }

    @Test("permissionsPublisher emits updates when map changes")
    func permissionsPublisherEmitsUpdates() async {
        let sut = makeSUT()
        var cancellables = Set<AnyCancellable>()
        var emitted: [[GGPermissionType: GGPermissionState]?] = []

        sut.permissionsPublisher
            .sink { emitted.append($0) }
            .store(in: &cancellables)

        sut.setPermissions([.CAMERA: .DISABLED])
        sut.updatePermission(.CAMERA, to: .ENABLED)
        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(cancellables.count == 1)
        #expect(emitted.count >= 3)
        #expect(emitted.last??[.CAMERA] == .ENABLED)
    }

    @Test("required categories are derived from connected scale types")
    func requiredCategoriesDerivedFromScaleTypes() {
        let scale = MockScaleService()
        scale.scales = [
            PermissionsTestFixtures.makeDevice(id: "bt", scaleType: .bluetoothScale).toSnapshot(),
            PermissionsTestFixtures.makeDevice(id: "wifi", scaleType: .wifi).toSnapshot(),
            PermissionsTestFixtures.makeDevice(id: "appsync", scaleType: .appsync).toSnapshot(),
            PermissionsTestFixtures.makeDevice(id: "r4", scaleType: .btWifiR4).toSnapshot()
        ]
        let sut = makeSUT(scale: scale)

        let categories = sut.getRequiredPermissionList()

        #expect(categories.contains(.bluetooth))
        #expect(categories.contains(.notifications))
        #expect(categories.contains(.camera))
    }

    @Test("required categories are cleared when scales become empty")
    func requiredCategoriesClearWhenNoScales() async {
        let scale = MockScaleService()
        scale.scales = [PermissionsTestFixtures.makeDevice(id: "wifi", scaleType: .wifi).toSnapshot()]
        let sut = makeSUT(scale: scale)
        #expect(sut.getRequiredPermissionList().contains(.notifications))

        scale.scales = []
        try? await Task.sleep(nanoseconds: 120_000_000)

        #expect(sut.getRequiredPermissionList().isEmpty)
    }

    @Test("permissionRequest maps enabled and disabled SDK results")
    func permissionRequestMapsSdkResults() async {
        let sdk = MockPermissionSDKClient()
        sdk.requestResults[.CAMERA] = GGPermissionState.ENABLED.rawValue
        sdk.requestResults[.LOCATION] = GGPermissionState.DISABLED.rawValue
        let sut = makeSUT(permissionClient: sdk)

        let enabled = await sut.permissionRequest(.CAMERA)
        let disabled = await sut.permissionRequest(.LOCATION)

        #expect(enabled == .ENABLED)
        #expect(disabled == .DISABLED)
        #expect(sdk.requestedPermissionTypes == [.CAMERA, .LOCATION])
    }

    @Test("permissionRequest treats unknown SDK response as disabled (unavailable edge)")
    func permissionRequestUnknownResponseDefaultsToDisabled() async {
        let sdk = MockPermissionSDKClient()
        sdk.requestResults[.NOTIFICATION] = "UNAVAILABLE"
        let sut = makeSUT(permissionClient: sdk)

        let result = await sut.permissionRequest(.NOTIFICATION)

        #expect(result == .DISABLED)
    }

    @Test("navigateToWifiSettings calls permission client with WIFI_SWITCH")
    func navigateToWifiSettingsCallsClient() async {
        let sdk = MockPermissionSDKClient()
        let sut = makeSUT(permissionClient: sdk)

        sut.navigateToWifiSettings()
        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(sdk.navigatedPermissionTypes == [.WIFI_SWITCH])
    }

    @Test("handlePermission bluetoothSwitch: dismiss returns cached state")
    func handleBluetoothSwitchReturnsCachedState() async {
        let notification = MockPermissionsNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.setPermissions(PermissionsTestFixtures.permissionMap(bluetoothSwitch: .ENABLED))

        let task = Task { await sut.handlePermission(.bluetoothSwitch) }
        await Task.yield()
        notification.tapAlertButton(at: 0)

        let result = await task.value

        #expect(notification.shownAlerts.count == 1)
        #expect(result == .ENABLED)
    }

    @Test("handlePermission bluetooth: permissions button requests and returns new state")
    func handleBluetoothPermissionRequests() async {
        let notification = MockPermissionsNotificationHelperService()
        let sdk = MockPermissionSDKClient()
        sdk.requestResults[.BLUETOOTH] = GGPermissionState.ENABLED.rawValue
        let sut = makeSUT(notification: notification, permissionClient: sdk)
        sut.setPermissions([.BLUETOOTH: .DISABLED])

        let task = Task { await sut.handlePermission(.bluetooth) }
        await Task.yield()
        notification.tapAlertButton(at: 1)

        let result = await task.value

        #expect(result == .ENABLED)
        #expect(sdk.requestedPermissionTypes == [.BLUETOOTH])
    }

    @Test("handlePermission notification: ignore path returns cached denied state")
    func handleNotificationIgnoreReturnsCachedState() async {
        let notification = MockPermissionsNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.setPermissions(PermissionsTestFixtures.permissionMap(notification: .DISABLED))

        let task = Task { await sut.handlePermission(.notification) }
        await Task.yield()
        notification.tapAlertButton(at: 0)

        let result = await task.value

        #expect(result == .DISABLED)
        #expect(notification.shownAlerts.count == 1)
    }

    @Test("handlePermission locationSwitch: why flow re-shows alert and final exit resolves")
    func handleLocationSwitchWhyFlow() async {
        let notification = MockPermissionsNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.setPermissions(PermissionsTestFixtures.permissionMap(locationSwitch: .DISABLED))

        let task = Task { await sut.handlePermission(.locationSwitch) }
        await Task.yield()
        notification.tapAlertButton(at: 1) // Why
        await Task.yield()
        notification.tapAlertButton(at: 0) // Back from Why
        await Task.yield()
        notification.tapAlertButton(at: 0) // Exit on re-shown location alert

        let result = await task.value

        #expect(result == .DISABLED)
        #expect(notification.shownAlerts.count == 3)
    }

    @Test("handlePermission location: permissions button requests updated state")
    func handleLocationPermissionRequests() async {
        let notification = MockPermissionsNotificationHelperService()
        let sdk = MockPermissionSDKClient()
        sdk.requestResults[.LOCATION] = GGPermissionState.ENABLED.rawValue
        let sut = makeSUT(notification: notification, permissionClient: sdk)

        let task = Task { await sut.handlePermission(.location) }
        await Task.yield()
        notification.tapAlertButton(at: 1)

        let result = await task.value

        #expect(result == .ENABLED)
        #expect(sdk.requestedPermissionTypes == [.LOCATION])
    }

    @Test("handlePermission camera: allow path requests permission")
    func handleCameraPermissionRequests() async {
        let notification = MockPermissionsNotificationHelperService()
        let sdk = MockPermissionSDKClient()
        sdk.requestResults[.CAMERA] = GGPermissionState.ENABLED.rawValue
        let sut = makeSUT(notification: notification, permissionClient: sdk)

        let task = Task { await sut.handlePermission(.camera) }
        await Task.yield()
        notification.tapAlertButton(at: 1)

        let result = await task.value

        #expect(result == .ENABLED)
        #expect(sdk.requestedPermissionTypes == [.CAMERA])
    }

    @Test("handlePermission wifi and internet share wifi disabled flow")
    func handleWifiAndInternetShareFlow() async {
        let notification = MockPermissionsNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.setPermissions(PermissionsTestFixtures.permissionMap(wifiSwitch: .DISABLED))

        let wifiTask = Task { await sut.handlePermission(.wifiSwitch) }
        await Task.yield()
        notification.tapAlertButton(at: 0)
        let wifiResult = await wifiTask.value

        let internetTask = Task { await sut.handlePermission(.internet) }
        await Task.yield()
        notification.tapAlertButton(at: 0)
        let internetResult = await internetTask.value

        #expect(wifiResult == .DISABLED)
        #expect(internetResult == .DISABLED)
        #expect(notification.shownAlerts.count == 2)
    }

    // MARK: - Helpers

    private func makeSUT(
        notification: MockPermissionsNotificationHelperService? = nil,
        scale: MockScaleService? = nil,
        logger: MockLoggerService? = nil,
        permissionClient: MockPermissionSDKClient? = nil
    ) -> PermissionsService {
        PermissionsService(
            notificationService: notification ?? MockPermissionsNotificationHelperService(),
            deviceService: scale ?? MockScaleService(),
            logger: logger ?? MockLoggerService(),
            permissionClient: permissionClient ?? MockPermissionSDKClient()
        )
    }

}
