import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct PermissionsStoreTests {
    @Test("initialization reads bluetooth permissions")
    func initializationReadsBluetoothPermissions() {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothEnabled)
        let store = makeSUT(permissionsService: permissionsService)

        #expect(store.isBluetoothAuthorized == true)
        #expect(store.isBluetoothOn == true)
    }

    @Test("updateBluetoothPermissions handles denied and unavailable states")
    func updateBluetoothPermissionsHandlesDeniedAndUnavailable() {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        let store = makeSUT(permissionsService: permissionsService)

        store.updateBluetoothPermissions()
        #expect(store.isBluetoothAuthorized == false)
        #expect(store.isBluetoothOn == false)

        permissionsService.emitPermissions(nil)
        store.updateBluetoothPermissions()
        #expect(store.isBluetoothAuthorized == false)
        #expect(store.isBluetoothOn == false)
    }

    @Test("required category publisher updates store")
    func requiredCategoryPublisherUpdatesStore() async {
        let permissionsService = MockPermissionsService()
        let store = makeSUT(permissionsService: permissionsService)
        let categories: Set<PermissionCategory> = [.bluetooth, .notifications]

        permissionsService.emitRequiredCategories(categories)
        let didUpdate = await waitUntil { store.requiredCategories == categories }

        #expect(didUpdate == true)
    }

    @Test("permissions publisher triggers bluetooth refresh")
    func permissionsPublisherTriggersBluetoothRefresh() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        let store = makeSUT(permissionsService: permissionsService)

        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothEnabled)
        let didUpdate = await waitUntil { store.isBluetoothAuthorized && store.isBluetoothOn }

        #expect(didUpdate == true)
    }

    @Test("handleBluetoothAuthorization requests permission and updates state")
    func handleBluetoothAuthorizationRequestsPermissionAndUpdatesState() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        permissionsService.handlePermissionResults[.bluetooth] = .ENABLED
        let store = makeSUT(permissionsService: permissionsService)

        await store.handleBluetoothAuthorization()

        #expect(permissionsService.handlePermissionCalls == [.bluetooth])
        #expect(store.isBluetoothAuthorized == true)
        #expect(store.isBluetoothOn == false)
    }

    @Test("handleBluetoothSwitch requests permission and updates switch state")
    func handleBluetoothSwitchRequestsPermissionAndUpdatesState() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        permissionsService.handlePermissionResults[.bluetoothSwitch] = .ENABLED
        let store = makeSUT(permissionsService: permissionsService)

        await store.handleBluetoothSwitch()

        #expect(permissionsService.handlePermissionCalls == [.bluetoothSwitch])
        #expect(store.isBluetoothAuthorized == false)
        #expect(store.isBluetoothOn == true)
    }

    @Test("handleBluetoothAuthorizationTap triggers async handler")
    func handleBluetoothAuthorizationTapTriggersAsyncHandler() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        permissionsService.handlePermissionResults[.bluetooth] = .ENABLED
        let store = makeSUT(permissionsService: permissionsService)

        store.handleBluetoothAuthorizationTap()
        let didCall = await waitUntil { permissionsService.handlePermissionCalls.contains(.bluetooth) }

        #expect(didCall == true)
        #expect(store.isBluetoothAuthorized == true)
        #expect(store.isBluetoothOn == false)
    }

    @Test("handleBluetoothSwitchTap triggers async handler and denied state remains false")
    func handleBluetoothSwitchTapTriggersAsyncHandlerAndDeniedStateRemainsFalse() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothEnabled)
        permissionsService.handlePermissionResults[.bluetoothSwitch] = .DISABLED
        let store = makeSUT(permissionsService: permissionsService)

        store.handleBluetoothSwitchTap()
        let didCall = await waitUntil { permissionsService.handlePermissionCalls.contains(.bluetoothSwitch) }

        #expect(didCall == true)
        #expect(store.isBluetoothAuthorized == true)
        #expect(store.isBluetoothOn == false)
    }

    @Test("handleBluetoothAuthorization handles denied permission result")
    func handleBluetoothAuthorizationHandlesDeniedPermissionResult() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        permissionsService.handlePermissionResults[.bluetooth] = .DISABLED
        let store = makeSUT(permissionsService: permissionsService)

        await store.handleBluetoothAuthorization()

        #expect(permissionsService.handlePermissionCalls == [.bluetooth])
        #expect(store.isBluetoothAuthorized == false)
        #expect(store.isBluetoothOn == false)
    }

    @Test("handleBluetoothSwitch handles denied permission result")
    func handleBluetoothSwitchHandlesDeniedPermissionResult() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        permissionsService.handlePermissionResults[.bluetoothSwitch] = .DISABLED
        let store = makeSUT(permissionsService: permissionsService)

        await store.handleBluetoothSwitch()

        #expect(permissionsService.handlePermissionCalls == [.bluetoothSwitch])
        #expect(store.isBluetoothAuthorized == false)
        #expect(store.isBluetoothOn == false)
    }

    @Test("handleBluetoothAuthorizationTap handles denied permission result")
    func handleBluetoothAuthorizationTapHandlesDeniedPermissionResult() async {
        let permissionsService = MockPermissionsService()
        permissionsService.emitPermissions(PermissionsStoreTestFixtures.bluetoothDisabled)
        permissionsService.handlePermissionResults[.bluetooth] = .DISABLED
        let store = makeSUT(permissionsService: permissionsService)

        store.handleBluetoothAuthorizationTap()
        let didCall = await waitUntil { permissionsService.handlePermissionCalls.contains(.bluetooth) }

        #expect(didCall == true)
        #expect(store.isBluetoothAuthorized == false)
        #expect(store.isBluetoothOn == false)
    }
}

@MainActor
private func makeSUT(permissionsService: MockPermissionsService? = nil) -> PermissionsStore {
    TestDependencyContainer.reset()

    let logger = MockLoggerService()
    let keychain = MockKeychainService()
    let bluetooth = MockBluetoothService()
    TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)

    let injectedPermissionsService = permissionsService ?? MockPermissionsService()
    DependencyContainer.shared.register(injectedPermissionsService as PermissionsServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

    return PermissionsStore()
}

@MainActor
private func waitUntil(
    timeoutIterations: Int = 200,
    condition: @MainActor () -> Bool
) async -> Bool {
    for _ in 0..<timeoutIterations {
        if condition() { return true }
        await Task.yield()
    }
    return false
}
