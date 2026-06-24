import Testing
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@Suite(.serialized)
@MainActor
struct PermissionsStoreTests {

    // MARK: - makeSUT

    private func makeSUT(
        btState: GGPermissionState = .DISABLED,
        btSwitchState: GGPermissionState = .DISABLED
    ) -> (PermissionsStore, MockSettingsPermissionsService, MockLoggerService) {
        let permissionsService = MockSettingsPermissionsService()
        if btState == .ENABLED || btSwitchState == .ENABLED {
            var perms: [GGPermissionType: GGPermissionState] = [:]
            perms[.BLUETOOTH] = btState
            perms[.BLUETOOTH_SWITCH] = btSwitchState
            permissionsService.permissions = perms
        }
        let logger = MockLoggerService()

        DependencyContainer.shared.register(permissionsService as PermissionsServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let store = PermissionsStore()
        return (store, permissionsService, logger)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 10_000_000,
        condition: @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while ContinuousClock.now < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }

    // MARK: - Initial state

    @Test("isBluetoothAuthorized false when BT disabled initially")
    func initialBtAuthorizedFalse() {
        let (store, _, _) = makeSUT(btState: .DISABLED)
        #expect(!store.isBluetoothAuthorized)
    }

    @Test("isBluetoothOn false when BT switch disabled initially")
    func initialBtSwitchFalse() {
        let (store, _, _) = makeSUT(btSwitchState: .DISABLED)
        #expect(!store.isBluetoothOn)
    }

    @Test("isBluetoothAuthorized true when BT enabled initially")
    func initialBtAuthorizedTrue() {
        let (store, _, _) = makeSUT(btState: .ENABLED)
        #expect(store.isBluetoothAuthorized)
    }

    @Test("isBluetoothOn true when BT switch enabled initially")
    func initialBtSwitchTrue() {
        let (store, _, _) = makeSUT(btSwitchState: .ENABLED)
        #expect(store.isBluetoothOn)
    }

    @Test("requiredCategories empty initially")
    func initialRequiredCategoriesEmpty() {
        let (store, _, _) = makeSUT()
        #expect(store.requiredCategories.isEmpty)
    }

    // MARK: - updateBluetoothPermissions

    @Test("updateBluetoothPermissions refreshes BT state from service")
    func updateBluetoothPermissionsRefreshes() {
        let (store, service, _) = makeSUT()
        service.grantBluetooth()
        store.updateBluetoothPermissions()
        #expect(store.isBluetoothAuthorized)
        #expect(store.isBluetoothOn)
    }

    @Test("updateBluetoothPermissions sets false when BT revoked")
    func updateBluetoothPermissionsRevoke() {
        let (store, service, _) = makeSUT(btState: .ENABLED, btSwitchState: .ENABLED)
        service.revokeBluetooth()
        store.updateBluetoothPermissions()
        #expect(!store.isBluetoothAuthorized)
        #expect(!store.isBluetoothOn)
    }

    // MARK: - requiredCategories publisher updates

    @Test("requiredCategories updated when publisher fires")
    func requiredCategoriesPublisherUpdate() async {
        let (store, service, _) = makeSUT()
        service.sendRequiredCategories([.bluetooth, .camera])
        await waitUntil { store.requiredCategories.count == 2 }
        #expect(store.requiredCategories.contains(.bluetooth))
        #expect(store.requiredCategories.contains(.camera))
    }

    @Test("requiredCategories cleared when publisher sends empty set")
    func requiredCategoriesClearedOnEmpty() async {
        let (store, service, _) = makeSUT()
        service.sendRequiredCategories([.bluetooth])
        await waitUntil { !store.requiredCategories.isEmpty }
        service.sendRequiredCategories([])
        await waitUntil { store.requiredCategories.isEmpty }
        #expect(store.requiredCategories.isEmpty)
    }

    // MARK: - permissionsPublisher → updateBluetoothPermissions

    @Test("BT state updates when permissionsPublisher fires with enabled BT")
    func permissionsPublisherUpdatesAuthorized() async {
        let (store, service, _) = makeSUT()
        service.grantBluetooth()
        await waitUntil { store.isBluetoothAuthorized }
        #expect(store.isBluetoothAuthorized)
        #expect(store.isBluetoothOn)
    }

    @Test("BT state clears when permissionsPublisher fires with disabled BT")
    func permissionsPublisherClearsBt() async {
        let (store, service, _) = makeSUT(btState: .ENABLED, btSwitchState: .ENABLED)
        service.revokeBluetooth()
        await waitUntil { !store.isBluetoothAuthorized }
        #expect(!store.isBluetoothAuthorized)
        #expect(!store.isBluetoothOn)
    }

    // MARK: - handleBluetoothAuthorization

    @Test("handleBluetoothAuthorization calls handlePermission(.bluetooth)")
    func handleBluetoothAuthorizationCallsService() async {
        let (store, service, _) = makeSUT()
        service.handlePermissionResult = .ENABLED
        await store.handleBluetoothAuthorization()
        #expect(service.handlePermissionCallCount == 1)
        #expect(service.lastHandledPermission == .bluetooth)
    }

    @Test("handleBluetoothAuthorization updates isBluetoothAuthorized after call")
    func handleBluetoothAuthorizationUpdatesState() async {
        let (store, service, _) = makeSUT()
        service.handlePermissionResult = .ENABLED
        await store.handleBluetoothAuthorization()
        #expect(store.isBluetoothAuthorized)
    }

    // MARK: - handleBluetoothSwitch

    @Test("handleBluetoothSwitch calls handlePermission(.bluetoothSwitch)")
    func handleBluetoothSwitchCallsService() async {
        let (store, service, _) = makeSUT()
        service.handlePermissionResult = .ENABLED
        await store.handleBluetoothSwitch()
        #expect(service.handlePermissionCallCount == 1)
        #expect(service.lastHandledPermission == .bluetoothSwitch)
    }

    @Test("handleBluetoothSwitch updates isBluetoothOn after call")
    func handleBluetoothSwitchUpdatesState() async {
        let (store, service, _) = makeSUT()
        service.handlePermissionResult = .ENABLED
        await store.handleBluetoothSwitch()
        #expect(store.isBluetoothOn)
    }

    // MARK: - handleBluetoothAuthorizationTap (fire-and-forget Task)

    @Test("handleBluetoothAuthorizationTap eventually calls service")
    func handleBluetoothAuthorizationTap() async {
        let (store, service, _) = makeSUT()
        service.handlePermissionResult = .ENABLED
        store.handleBluetoothAuthorizationTap()
        await waitUntil { service.handlePermissionCallCount >= 1 }
        #expect(service.handlePermissionCallCount >= 1)
    }

    // MARK: - handleBluetoothSwitchTap (fire-and-forget Task)

    @Test("handleBluetoothSwitchTap eventually calls service")
    func handleBluetoothSwitchTap() async {
        let (store, service, _) = makeSUT()
        service.handlePermissionResult = .ENABLED
        store.handleBluetoothSwitchTap()
        await waitUntil { service.handlePermissionCallCount >= 1 }
        #expect(service.handlePermissionCallCount >= 1)
    }
}
