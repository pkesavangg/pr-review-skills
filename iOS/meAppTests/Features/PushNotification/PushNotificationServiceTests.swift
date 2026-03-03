import Foundation
import Testing
@testable import meApp

@MainActor
struct PushNotificationServiceTests {
    @Test("handleNotification data-only: syncs entries/scales and shows local banner")
    func handleNotificationDataOnly() async {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        await handleNotificationAndWait(sut, userInfo: PushNotificationTestFixtures.makeDataOnlyUserInfo())

        #expect(deps.entry.syncAllEntriesCalls == 1)
        #expect(deps.scale.syncAllScalesCalls == 1)
        #expect(deps.center.addCalls == 1)
    }

    @Test("handleNotification with alert payload: does not create local notification")
    func handleNotificationWithAlertPayload() async {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        await handleNotificationAndWait(sut, userInfo: PushNotificationTestFixtures.makeAlertUserInfo())

        #expect(deps.entry.syncAllEntriesCalls == 1)
        #expect(deps.scale.syncAllScalesCalls == 1)
        #expect(deps.center.addCalls == 0)
    }

    @Test("handleNotification duplicate messageId: second notification is skipped")
    func handleNotificationDuplicateMessageId() async {
        let deps = makeDependencies()
        let sut = makeSUT(deps)
        let payload = PushNotificationTestFixtures.makeDataOnlyUserInfo(messageId: "dup-1")

        await handleNotificationAndWait(sut, userInfo: payload)
        await handleNotificationAndWait(sut, userInfo: payload)

        #expect(deps.entry.syncAllEntriesCalls == 1)
        #expect(deps.scale.syncAllScalesCalls == 1)
        #expect(deps.center.addCalls == 1)
    }

    @Test("handleNotification while already processing: second request exits early")
    func handleNotificationAlreadyProcessing() async {
        let deps = makeDependencies()
        deps.entry.shouldSuspendSync = true
        let sut = makeSUT(deps)

        var firstCompleted = false
        sut.handleNotification(PushNotificationTestFixtures.makeDataOnlyUserInfo(messageId: "in-flight")) {
            firstCompleted = true
        }
        for _ in 0..<40 where deps.entry.syncAllEntriesCalls == 0 {
            await Task.yield()
        }

        await handleNotificationAndWait(sut, userInfo: PushNotificationTestFixtures.makeDataOnlyUserInfo(messageId: "second"))
        deps.entry.releaseSync()
        for _ in 0..<40 where !firstCompleted {
            await Task.yield()
        }

        #expect(firstCompleted == true)
        #expect(deps.entry.syncAllEntriesCalls == 1)
        #expect(deps.scale.syncAllScalesCalls == 1)
    }

    @Test("handleNotificationTap posts destination notification")
    func handleNotificationTapPostsDestination() {
        let deps = makeDependencies()
        let sut = makeSUT(deps)
        var receivedDestination: String?
        let observer = NotificationCenter.default.addObserver(
            forName: .didReceiveNotification,
            object: nil,
            queue: nil
        ) { notification in
            receivedDestination = notification.userInfo?["destination"] as? String
        }
        defer { NotificationCenter.default.removeObserver(observer) }

        sut.handleNotificationTap(["destination": "settings"])

        #expect(receivedDestination == "settings")
    }

    @Test("setupPushNotifications token already loaded: updates device info and skips register")
    func setupPushNotificationsTokenAlreadyLoaded() async {
        let deps = makeDependencies()
        deps.keychain.setFCMToken("existing-token", for: "acct-1")
        let sut = makeSUT(deps)

        await sut.setupPushNotifications(isFromScaleSetup: false)

        #expect(deps.api.updateDeviceInfoCalls == 1)
        #expect(deps.registrar.registerCalls == 0)
        #expect(deps.tokenProvider.fetchCalls == 0)
    }

    @Test("setupPushNotifications success flow: requests token, stores keychain token, updates device info")
    func setupPushNotificationsSuccess() async {
        let deps = makeDependencies()
        deps.permissions.currentState = .ENABLED
        deps.tokenProvider.fetchTokenResult = .success("fresh-fcm-token")
        let sut = makeSUT(deps)

        await sut.setupPushNotifications(isFromScaleSetup: false)

        #expect(deps.registrar.registerCalls == 1)
        #expect(deps.tokenProvider.fetchCalls == 1)
        #expect(deps.keychain.getFCMToken(for: "acct-1") == "fresh-fcm-token")
        #expect(deps.api.updateDeviceInfoCalls >= 2)
    }

    @Test("setupPushNotifications from scale setup: requests permission even if alert seen")
    func setupPushNotificationsFromScaleSetupRequestsPermission() async {
        let deps = makeDependencies()
        deps.permissions.currentState = .DISABLED
        deps.permissions.handlePermissionResult = .ENABLED
        deps.kv.setValue(true, forKey: KvStorageKeys.notificationOnlyAlertShownKey(for: "acct-1"))
        let sut = makeSUT(deps)

        await sut.setupPushNotifications(isFromScaleSetup: true)

        #expect(deps.permissions.handlePermissionCalls == 1)
        #expect(deps.permissions.lastHandledPermission == .notification)
    }

    @Test("setupPushNotifications category not required: skips permission handling and register")
    func setupPushNotificationsCategoryNotRequired() async {
        let deps = makeDependencies()
        deps.permissions.requiredCategories = []
        let sut = makeSUT(deps)

        await sut.setupPushNotifications(isFromScaleSetup: false)

        #expect(deps.permissions.handlePermissionCalls == 0)
        #expect(deps.registrar.registerCalls == 0)
    }

    @Test("setupPushNotifications permission mismatch with authorization: does not register")
    func setupPushNotificationsNotAuthorized() async {
        let deps = makeDependencies()
        deps.permissions.currentState = .DISABLED
        deps.permissions.handlePermissionResult = .ENABLED
        deps.permissions.forcedGetPermissionState = .DISABLED
        let sut = makeSUT(deps)

        await sut.setupPushNotifications(isFromScaleSetup: false)

        #expect(deps.registrar.registerCalls == 0)
        #expect(deps.tokenProvider.fetchCalls == 0)
    }

    @Test("getStoredFCMToken reads from keychain first")
    func getStoredFCMTokenKeychain() {
        let deps = makeDependencies()
        deps.keychain.setFCMToken("token-keychain", for: "acct-1")
        let sut = makeSUT(deps)

        let result = sut.getStoredFCMToken(for: "acct-1")

        #expect(result == "token-keychain")
    }

    @Test("getStoredFCMToken migrates token from KvStorage to keychain")
    func getStoredFCMTokenMigratesFromKvStorage() {
        let deps = makeDependencies()
        let legacyKey = KvStorageKeys.fcmTokenKey(for: "acct-1")
        deps.kv.setValue("legacy-token", forKey: legacyKey)
        let sut = makeSUT(deps)

        let result = sut.getStoredFCMToken(for: "acct-1")

        #expect(result == "legacy-token")
        #expect(deps.keychain.getFCMToken(for: "acct-1") == "legacy-token")
        #expect(deps.kv.getValue(forKey: legacyKey) == nil)
    }

    @Test("getStoredFCMToken returns nil when no token exists")
    func getStoredFCMTokenMissing() {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        #expect(sut.getStoredFCMToken(for: "acct-1") == nil)
    }

    @Test("token refresh notification stores token and updates device info")
    func tokenRefreshNotificationStoresAndUpdates() async {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        NotificationCenter.default.post(
            name: PushNotificationService.fcmTokenDidRefresh,
            object: nil,
            userInfo: ["token": "refreshed-token"]
        )
        for _ in 0..<40 where deps.api.updateDeviceInfoCalls == 0 {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 5_000_000)
        }

        #expect(deps.keychain.getFCMToken(for: "acct-1") == "refreshed-token")
        #expect(deps.api.updateDeviceInfoCalls >= 1)
        _ = sut
    }

    private func handleNotificationAndWait(_ sut: PushNotificationService, userInfo: [AnyHashable: Any]) async {
        await withCheckedContinuation { continuation in
            sut.handleNotification(userInfo) {
                continuation.resume(returning: ())
            }
        }
    }

    private func makeDependencies() -> PushTestDependencies {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(
            id: "acct-1",
            email: "push@example.com",
            isActive: true
        )
        return PushTestDependencies(
            api: MockPushNotificationAPIRepository(),
            entry: MockPushEntryService(),
            permissions: MockPushPermissionsService(),
            account: account,
            notifications: MockNotificationHelperService(),
            bluetooth: MockBluetoothService(),
            scale: MockPushScaleService(),
            keychain: MockKeychainService(),
            kv: MockKvStorageService(),
            logger: MockLoggerService(),
            tokenProvider: MockPushTokenProvider(),
            registrar: MockPushRegistrar(),
            center: MockPushUserNotificationCenter()
        )
    }

    private func makeSUT(_ deps: PushTestDependencies) -> PushNotificationService {
        PushNotificationService(
            apiRepo: deps.api,
            entryService: deps.entry,
            permissionsService: deps.permissions,
            accountService: deps.account,
            notificationService: deps.notifications,
            bluetoothService: deps.bluetooth,
            scaleService: deps.scale,
            keychainService: deps.keychain,
            kvStorage: deps.kv,
            logger: deps.logger,
            tokenProvider: deps.tokenProvider,
            pushRegistrar: deps.registrar,
            userNotificationCenter: deps.center,
            setupNetworkMonitoring: false
        )
    }
}

private struct PushTestDependencies {
    let api: MockPushNotificationAPIRepository
    let entry: MockPushEntryService
    let permissions: MockPushPermissionsService
    let account: MockAccountService
    let notifications: MockNotificationHelperService
    let bluetooth: MockBluetoothService
    let scale: MockPushScaleService
    let keychain: MockKeychainService
    let kv: MockKvStorageService
    let logger: MockLoggerService
    let tokenProvider: MockPushTokenProvider
    let registrar: MockPushRegistrar
    let center: MockPushUserNotificationCenter
}
