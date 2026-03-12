import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
struct WifiScaleSetupStoreHarness {
    let store: WifiScaleSetupStore
    let notification: TestNotificationHelperService
    let permissions: MockPermissionsService
    let wifiScaleService: MockWifiScaleService
    let accountService: MockAccountService
    let scaleService: MockScaleService
    let pushNotifications: MockPushNotificationService
    let networkMonitor: MockNetworkMonitor
    let bluetoothService: MockBluetoothService
    let httpClient: MockHTTPClient
    let logger: MockLoggerService
}

@MainActor
struct WifiScaleSetupContainerDependencySet {
    let notification: TestNotificationHelperService
    let permissions: MockPermissionsService
    let wifiScaleService: MockWifiScaleService
    let accountService: MockAccountService
    let logger: MockLoggerService
    let scaleService: MockScaleService
    let pushNotifications: MockPushNotificationService
    let httpClient: MockHTTPClient
    let bluetoothService: MockBluetoothService
}

enum WifiScaleSetupStoreTestFixtures {
    @MainActor
    static func makeSUT(
        notification: TestNotificationHelperService? = nil,
        permissions: MockPermissionsService? = nil,
        wifiScaleService: MockWifiScaleService? = nil,
        accountService: MockAccountService? = nil,
        scaleService: MockScaleService? = nil,
        pushNotifications: MockPushNotificationService? = nil,
        networkMonitor: MockNetworkMonitor? = nil,
        bluetoothService: MockBluetoothService? = nil,
        httpClient: MockHTTPClient? = nil,
        logger: MockLoggerService? = nil
    ) -> WifiScaleSetupStoreHarness {
        let notification = notification ?? TestNotificationHelperService()
        let permissions = permissions ?? MockPermissionsService()
        let wifiScaleService = wifiScaleService ?? MockWifiScaleService()
        let accountService = accountService ?? MockAccountService()
        let scaleService = scaleService ?? MockScaleService()
        let pushNotifications = pushNotifications ?? MockPushNotificationService()
        let networkMonitor = networkMonitor ?? MockNetworkMonitor(isConnected: true)
        let bluetoothService = bluetoothService ?? MockBluetoothService()
        let httpClient = httpClient ?? MockHTTPClient()
        let logger = logger ?? MockLoggerService()

        if accountService.activeAccount == nil {
            let activeAccount = makeAccount()
            accountService.seedAccounts([activeAccount], active: activeAccount)
        }

        if permissions.permissions == nil {
            permissions.setPermissions(enabledPermissions())
        }

        let store = WifiScaleSetupStore(
            notificationService: notification,
            permissionsService: permissions,
            wifiScaleService: wifiScaleService,
            accountService: accountService,
            logger: logger,
            scaleService: scaleService,
            pushNotificationService: pushNotifications,
            httpClient: httpClient,
            bluetoothService: bluetoothService,
            networkMonitor: networkMonitor
        )

        return WifiScaleSetupStoreHarness(
            store: store,
            notification: notification,
            permissions: permissions,
            wifiScaleService: wifiScaleService,
            accountService: accountService,
            scaleService: scaleService,
            pushNotifications: pushNotifications,
            networkMonitor: networkMonitor,
            bluetoothService: bluetoothService,
            httpClient: httpClient,
            logger: logger
        )
    }

    @MainActor
    static func makeAccount(
        id: String = "acct-1",
        email: String = "wifi-setup@example.com",
        firstName: String = "Lakshmi"
    ) -> Account {
        AccountTestFixtures.makeAccountModel(id: id, email: email, firstName: firstName, isActive: true)
    }

    static func enabledPermissions() -> [GGPermissionType: GGPermissionState] {
        [
            .LOCATION: .ENABLED,
            .LOCATION_SWITCH: .ENABLED,
            .WIFI_SWITCH: .ENABLED
        ]
    }

    static func disabledPermissions() -> [GGPermissionType: GGPermissionState] {
        [
            .LOCATION: .DISABLED,
            .LOCATION_SWITCH: .DISABLED,
            .WIFI_SWITCH: .DISABLED
        ]
    }

    @MainActor
    static func configureDefaultWifiScale(_ store: WifiScaleSetupStore) {
        store.configure(with: "0385")
    }

    @MainActor
    @discardableResult
    static func registerDefaultContainerDependencies() -> WifiScaleSetupContainerDependencySet {
        let notification = TestNotificationHelperService()
        let permissions = MockPermissionsService()
        let wifiScaleService = MockWifiScaleService()
        let accountService = MockAccountService()
        let logger = MockLoggerService()
        let scaleService = MockScaleService()
        let pushNotifications = MockPushNotificationService()
        let httpClient = MockHTTPClient()
        let bluetoothService = MockBluetoothService()

        let activeAccount = makeAccount()
        accountService.seedAccounts([activeAccount], active: activeAccount)
        permissions.setPermissions(enabledPermissions())

        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(wifiScaleService as WifiScaleServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(pushNotifications as PushNotificationServiceProtocol)
        DependencyContainer.shared.register(httpClient as HTTPClientProtocol)
        DependencyContainer.shared.register(bluetoothService as BluetoothServiceProtocol)

        return WifiScaleSetupContainerDependencySet(
            notification: notification,
            permissions: permissions,
            wifiScaleService: wifiScaleService,
            accountService: accountService,
            logger: logger,
            scaleService: scaleService,
            pushNotifications: pushNotifications,
            httpClient: httpClient,
            bluetoothService: bluetoothService
        )
    }

    @MainActor
    static func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }
}
