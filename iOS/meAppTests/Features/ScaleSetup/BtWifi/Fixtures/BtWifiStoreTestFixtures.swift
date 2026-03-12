import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
struct BtWifiStoreTestHarness {
    let store: BtWifiScaleSetupStore
    let notification: TestNotificationHelperService
    let permissions: MockPermissionsService
    let bluetooth: MockBluetoothService
    let account: MockAccountService
    let scaleService: MockScaleService
    let wifiScaleService: MockWifiScaleService
    let pushNotifications: MockPushNotificationService
    let entryService: MockEntryService
    let goalAlertService: MockGoalAlertService
    let networkMonitor: MockNetworkMonitor
    let bluetoothSetupManager: MockBtWifiBluetoothSetupManager
}

@MainActor
final class MockBtWifiBluetoothSetupManager: BluetoothSetupManaging {
    private(set) var disconnectIfNeededCalls = 0
    private(set) var cancelWifiCalls = 0
    private(set) var lastDisconnectedBroadcastId: String?
    private(set) var lastDisconnectConsiderForSession: Bool?
    private(set) var lastCancelledScale: Device?

    func disconnectIfNeeded(
        broadcastId: String,
        bluetoothService: BluetoothServiceProtocol,
        considerForSession: Bool
    ) async {
        disconnectIfNeededCalls += 1
        lastDisconnectedBroadcastId = broadcastId
        lastDisconnectConsiderForSession = considerForSession
    }

    func cancelWifi(on scale: Device, bluetoothService: BluetoothServiceProtocol) async {
        cancelWifiCalls += 1
        lastCancelledScale = scale
    }
}

enum BtWifiStoreTestFixtures {
    @MainActor
    static func makeSUT(
        notification: TestNotificationHelperService? = nil,
        permissions: MockPermissionsService? = nil,
        bluetooth: MockBluetoothService? = nil,
        account: MockAccountService? = nil,
        scaleService: MockScaleService? = nil,
        wifiScaleService: MockWifiScaleService? = nil,
        pushNotifications: MockPushNotificationService? = nil,
        entryService: MockEntryService? = nil,
        goalAlertService: MockGoalAlertService? = nil,
        networkMonitor: MockNetworkMonitor? = nil,
        bluetoothSetupManager: MockBtWifiBluetoothSetupManager? = nil,
        dashboardStoreFactory: (@MainActor () -> DashboardStore)? = nil
    ) -> BtWifiStoreTestHarness {
        TestDependencyContainer.reset()

        let notification = notification ?? TestNotificationHelperService()
        let permissions = permissions ?? MockPermissionsService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let account = account ?? MockAccountService()
        let scaleService = scaleService ?? MockScaleService()
        let wifiScaleService = wifiScaleService ?? MockWifiScaleService()
        let pushNotifications = pushNotifications ?? MockPushNotificationService()
        let entryService = entryService ?? MockEntryService()
        let goalAlertService = goalAlertService ?? MockGoalAlertService()
        let networkMonitor = networkMonitor ?? MockNetworkMonitor(isConnected: true)
        let bluetoothSetupManager = bluetoothSetupManager ?? MockBtWifiBluetoothSetupManager()

        if account.activeAccount == nil {
            let activeAccount = makeAccount()
            account.seedAccounts([activeAccount], active: activeAccount)
        }

        if permissions.permissions == nil {
            permissions.setPermissions([
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .ENABLED
            ])
        }

        DependencyContainer.shared.register(notification as NotificationHelperService)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(account as AccountServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(wifiScaleService as WifiScaleServiceProtocol)
        DependencyContainer.shared.register(pushNotifications as PushNotificationServiceProtocol)
        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(goalAlertService as GoalAlertServiceProtocol)

        let store = BtWifiScaleSetupStore(
            bluetoothSetupManager: bluetoothSetupManager,
            networkMonitor: networkMonitor,
            dashboardStoreFactory: dashboardStoreFactory ?? { fatalError("Dashboard store should not be used in BtWifiScaleSetupStore unit tests") }
        )

        // Prime injector-backed dependencies immediately to avoid cross-suite DI races
        // when async tasks resolve them later.
        _ = store.notificationService
        _ = store.permissionsService
        _ = store.bluetoothService
        _ = store.accountService
        _ = store.wifiScaleService
        _ = store.scaleService
        _ = store.pushNotificationService
        _ = store.entryService
        _ = store.goalAlertService

        return BtWifiStoreTestHarness(
            store: store,
            notification: notification,
            permissions: permissions,
            bluetooth: bluetooth,
            account: account,
            scaleService: scaleService,
            wifiScaleService: wifiScaleService,
            pushNotifications: pushNotifications,
            entryService: entryService,
            goalAlertService: goalAlertService,
            networkMonitor: networkMonitor,
            bluetoothSetupManager: bluetoothSetupManager
        )
    }

    @MainActor
    static func makeAccount(
        id: String = "acct-1",
        email: String = "btwifi@example.com",
        firstName: String = "Lakshmi"
    ) -> Account {
        let account = AccountTestFixtures.makeAccountModel(id: id, email: email, firstName: firstName, isActive: true)
        // Keep BtWifi unit tests isolated from DashboardStore initialization path.
        account.dashboardSettings?.dashboardType = DashboardType.dashboard12.rawValue
        return account
    }

    static func makeScale(
        id: String = "scale-1",
        accountId: String = "acct-1",
        displayName: String = "Bathroom Scale",
        token: String = "token-1"
    ) -> Device {
        ScaleTestFixtures.makeDevice(id: id, accountId: accountId, displayName: displayName, token: token)
    }

    static func makeDiscoveryEvent(
        scale: Device = makeScale(),
        isNew: Bool = true
    ) -> DeviceDiscoveryEvent {
        DeviceDiscoveryEvent(
            device: scale,
            deviceInfo: SCALES.first { $0.setupType == .btWifiR4 } ?? SCALES[0],
            protocolType: .R4,
            isNew: isNew
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
