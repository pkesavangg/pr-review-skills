import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct ScaleSettingsStoreTests {
    @Test("initial state uses cached preference values and bluetooth permission gating")
    func initialStateUsesCachedValues() {
        let scale = makeR4Scale()
        scale.isConnected = true
        let permissions = MockPermissionsService()
        permissions.setPermissions([.BLUETOOTH_SWITCH: .DISABLED])

        let (store, _, _, _, _) = makeSUT(scale: scale, permissions: permissions)

        #expect(store.displayName == "Primary Scale")
        #expect(store.isBodyMetrics == true)
        #expect(store.cachedShouldMeasurePulse == false)
        #expect(store.isDeviceConnected == false)
        #expect(store.isWifiConfigured == true)
    }

    @Test("refreshScaleData falls back to active account name when preference is missing")
    func refreshScaleDataFallsBackToActiveAccountName() {
        let scale = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1")
        scale.bathScale = BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        scale.r4ScalePreference = nil

        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "user@example.com", firstName: "Lakshmi", isActiveAccount: true)

        let (store, _, _, _, _) = makeSUT(scale: scale, account: account)

        store.refreshScaleData()

        #expect(store.displayName == "Lakshmi")
        #expect(store.cachedShouldMeasureImpedance == false)
        #expect(store.cachedPreferenceIsSynced == false)
    }

    @Test("openProductGuide sets browser state")
    func openProductGuideSetsBrowserState() {
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale())

        store.openProductGuide(for: "R4-001")

        #expect(store.showProductBrowser == true)
        #expect(store.productURL?.absoluteString.hasSuffix("/R4-001") == true)
    }

    @Test("ensureWifiMacAddress fetches once and caches result")
    func ensureWifiMacAddressFetchesOnce() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getWifiMacAddressResult = .success("AA:BB:CC:DD:EE:FF")
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), bluetooth: bluetooth)

        await store.ensureWifiMacAddress()
        await store.ensureWifiMacAddress()

        #expect(bluetooth.getWifiMacAddressCalls == 1)
        #expect(store.wifiMacAddress == "AA:BB:CC:DD:EE:FF")
    }

    @Test("ensureWifiMacAddress failure keeps value nil")
    func ensureWifiMacAddressFailureKeepsValueNil() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getWifiMacAddressResult = .failure(.notImplemented)
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), bluetooth: bluetooth)

        await store.ensureWifiMacAddress()

        #expect(bluetooth.getWifiMacAddressCalls == 1)
        #expect(store.wifiMacAddress == nil)
        #expect(store.isFetchingWifiMacAddress == false)
    }

    @Test("ensureUsersList returns empty when scale is not connected")
    func ensureUsersListReturnsEmptyWhenDisconnected() async {
        let scale = makeR4Scale()
        scale.isConnected = false
        let bluetooth = MockBluetoothService()
        let (store, _, _, _, _) = makeSUT(scale: scale, bluetooth: bluetooth)

        let users = await store.ensureUsersList()

        #expect(users.isEmpty)
        #expect(store.usersList.isEmpty)
        #expect(bluetooth.getScaleUserListCalls == 0)
    }

    @Test("ensureUsersList stores fetched users for connected R4 scale")
    func ensureUsersListStoresFetchedUsers() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getScaleUserListResult = .success([
            DeviceUser(name: "John", token: "token-1", lastActive: 1, isBodyMetricsEnabled: true),
            DeviceUser(name: "Jane", token: "token-2", lastActive: 2, isBodyMetricsEnabled: false)
        ])
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), bluetooth: bluetooth)

        let users = await store.ensureUsersList()

        #expect(bluetooth.getScaleUserListCalls == 1)
        #expect(users.count == 2)
        #expect(store.usersList.map(\.name) == ["John", "Jane"])
    }

    @Test("ensureUsersList failure clears users and returns empty")
    func ensureUsersListFailureClearsUsers() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getScaleUserListResult = .failure(.notImplemented)
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), bluetooth: bluetooth)

        let users = await store.ensureUsersList()

        #expect(bluetooth.getScaleUserListCalls == 1)
        #expect(users.isEmpty)
        #expect(store.usersList.isEmpty)
        #expect(store.isFetchingUsersList == false)
    }

    @Test("getDeviceInfo success updates published state and syncs unsynced preferences")
    func getDeviceInfoSuccessUpdatesStateAndSyncsPreferences() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getDeviceInfoResult = .success(
            DeviceInfo(
                firmwareRevision: "2.0.1",
                deviceName: "R4",
                broadcastIdString: "40E20100",
                macAddress: "AA:BB:CC:DD:EE:FF",
                isWifiConfigured: true,
                sessionImpedanceSwitchState: true,
                impedanceSwitchState: false
            )
        )
        bluetooth.getConnectedWifiSSIDResult = .success("Home WiFi")
        bluetooth.updateAccountResult = .success(.creationCompleted)
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(preferenceSynced: false), scaleService: scaleService, bluetooth: bluetooth)

        await store.getDeviceInfo()
        await Task.yield()
        await Task.yield()

        #expect(bluetooth.getDeviceInfoCalls == 1)
        #expect(bluetooth.getConnectedWifiSSIDCalls == 1)
        #expect(bluetooth.updateAccountCalls == 1)
        #expect(store.firmwareVersion == "2.0.1")
        #expect(store.connectedWifiSSID == "Home WiFi")
        #expect(store.isWifiConfigured == true)
        #expect(store.isImpedanceSwitchedOnForSession == true)
        #expect(store.isScaleImpedanceSwitchedOn == false)
        #expect(store.isWeighOnlyModeEnabledByOthers == true)
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(scaleService.lastUpdatedScalePreferenceDTO?.isSynced == true)
    }

    @Test("getDeviceInfo returns early when scale is disconnected")
    func getDeviceInfoReturnsEarlyWhenDisconnected() async {
        let scale = makeR4Scale()
        scale.isConnected = false
        let bluetooth = MockBluetoothService()
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: scale, scaleService: scaleService, bluetooth: bluetooth)

        await store.getDeviceInfo()

        #expect(bluetooth.getDeviceInfoCalls == 0)
        #expect(bluetooth.getConnectedWifiSSIDCalls == 0)
        #expect(bluetooth.updateAccountCalls == 0)
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 0)
    }

    @Test("getDeviceInfo failure does not fetch wifi or sync preferences")
    func getDeviceInfoFailureDoesNotFetchWifiOrSyncPreferences() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getDeviceInfoResult = .failure(.notImplemented)
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(preferenceSynced: false), scaleService: scaleService, bluetooth: bluetooth)

        await store.getDeviceInfo()

        #expect(bluetooth.getDeviceInfoCalls == 1)
        #expect(bluetooth.getConnectedWifiSSIDCalls == 0)
        #expect(bluetooth.updateAccountCalls == 0)
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 0)
        #expect(store.deviceInfo == nil)
        #expect(store.connectedWifiSSID == nil)
    }

    @Test("getDeviceInfo skips preference sync when already synced")
    func getDeviceInfoSkipsPreferenceSyncWhenAlreadySynced() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getDeviceInfoResult = .success(
            DeviceInfo(
                firmwareRevision: "2.0.1",
                deviceName: "R4",
                broadcastIdString: "40E20100",
                macAddress: "AA:BB:CC:DD:EE:FF",
                isWifiConfigured: true,
                sessionImpedanceSwitchState: false,
                impedanceSwitchState: true
            )
        )
        bluetooth.getConnectedWifiSSIDResult = .success("Home WiFi")
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(preferenceSynced: true), scaleService: scaleService, bluetooth: bluetooth)

        await store.getDeviceInfo()
        await Task.yield()

        #expect(bluetooth.getDeviceInfoCalls == 1)
        #expect(bluetooth.updateAccountCalls == 0)
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 0)
        #expect(store.isScaleImpedanceSwitchedOn == true)
    }

    @Test("getDeviceInfo sync failure does not mark preference synced")
    func getDeviceInfoSyncFailureDoesNotMarkPreferenceSynced() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getDeviceInfoResult = .success(
            DeviceInfo(
                firmwareRevision: "2.0.1",
                deviceName: "R4",
                broadcastIdString: "40E20100",
                macAddress: "AA:BB:CC:DD:EE:FF",
                isWifiConfigured: true,
                sessionImpedanceSwitchState: false,
                impedanceSwitchState: false
            )
        )
        bluetooth.updateAccountResult = .failure(.notImplemented)
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(preferenceSynced: false), scaleService: scaleService, bluetooth: bluetooth)

        await store.getDeviceInfo()
        await Task.yield()

        #expect(bluetooth.updateAccountCalls == 1)
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 0)
        #expect(store.deviceInfo?.firmwareRevision == "2.0.1")
    }

    @Test("scale publisher event triggers device info refresh for matching scale")
    func scalePublisherEventTriggersDeviceInfoRefresh() async {
        let bluetooth = MockBluetoothService()
        bluetooth.getDeviceInfoResult = .success(
            DeviceInfo(
                firmwareRevision: "3.1.0",
                deviceName: "R4",
                broadcastIdString: "40E20100",
                macAddress: "AA:BB:CC:DD:EE:FF",
                isWifiConfigured: true,
                sessionImpedanceSwitchState: false,
                impedanceSwitchState: true
            )
        )
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), scaleService: scaleService, bluetooth: bluetooth)
        scaleService.scales = [makeR4Scale()]

        await waitUntil(timeoutNanoseconds: 500_000_000) {
            bluetooth.getDeviceInfoCalls > 0
        }

        #expect(bluetooth.getDeviceInfoCalls > 0)
        #expect(store.firmwareVersion == "3.1.0")
    }

    @Test("handleEnableBodyMetrics shows alert and primary action enables session")
    func handleEnableBodyMetricsShowsAlertAndRunsPrimaryAction() async {
        let notification = MockNotificationHelperService()
        let bluetooth = MockBluetoothService()
        bluetooth.updateWeightOnlyModeResult = .success(())
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), notification: notification, bluetooth: bluetooth)

        store.handleEnableBodyMetrics()
        notification.alertData?.buttons.first?.action(nil)
        await Task.yield()

        #expect(notification.showAlertCalls == 1)
        #expect(bluetooth.updateWeightOnlyModeCalls == 1)
        #expect(notification.showToastCalls == 1)
    }

    @Test("handleEnableBodyMetrics does nothing when scale is disconnected")
    func handleEnableBodyMetricsDoesNothingWhenDisconnected() async {
        let scale = makeR4Scale()
        scale.isConnected = false
        let notification = MockNotificationHelperService()
        let bluetooth = MockBluetoothService()
        let (store, _, _, _, _) = makeSUT(scale: scale, notification: notification, bluetooth: bluetooth)

        store.handleEnableBodyMetrics()
        notification.alertData?.buttons.first?.action(nil)
        await Task.yield()

        #expect(notification.showAlertCalls == 1)
        #expect(bluetooth.updateWeightOnlyModeCalls == 0)
        #expect(notification.showToastCalls == 0)
    }

    @Test("handleEnableBodyMetrics failure does not show toast")
    func handleEnableBodyMetricsFailureDoesNotShowToast() async {
        let notification = MockNotificationHelperService()
        let bluetooth = MockBluetoothService()
        bluetooth.updateWeightOnlyModeResult = .failure(.notImplemented)
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), notification: notification, bluetooth: bluetooth)

        store.handleEnableBodyMetrics()
        notification.alertData?.buttons.first?.action(nil)
        await Task.yield()

        #expect(bluetooth.updateWeightOnlyModeCalls == 1)
        #expect(notification.showToastCalls == 0)
    }

    @Test("handleScaleDelete confirms deletion and triggers service sync flow")
    func handleScaleDeleteRunsDeleteFlow() async {
        let notification = MockNotificationHelperService()
        let bluetooth = MockBluetoothService()
        bluetooth.resyncAndScanResult = .success(())
        let scaleService = MockScaleService()
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), notification: notification, scaleService: scaleService, bluetooth: bluetooth)
        var didSucceed = false

        store.handleScaleDelete(scaleId: "scale-1") {
            didSucceed = true
        }
        notification.alertData?.buttons.first?.action(nil)
        await waitUntil(timeoutNanoseconds: 2_000_000_000) {
            scaleService.deleteDeviceCalls == 1 &&
            scaleService.pushLocalChangesToServerCalls == 1 &&
            scaleService.syncAllScalesWithRemoteCalls == 1 &&
            bluetooth.resyncAndScanCalls == 1 &&
            notification.dismissLoaderCalls == 1 &&
            didSucceed
        }

        #expect(notification.showLoaderCalls == 1)
        #expect(scaleService.deleteDeviceCalls == 1)
        #expect(scaleService.lastDeletedDeviceId == "scale-1")
        #expect(scaleService.pushLocalChangesToServerCalls == 1)
        #expect(scaleService.syncAllScalesWithRemoteCalls == 1)
        #expect(bluetooth.resyncAndScanCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(didSucceed == true)
    }

    @Test("handleScaleDelete failure shows error toast and does not call success")
    func handleScaleDeleteFailureShowsErrorToast() async {
        let notification = MockNotificationHelperService()
        let bluetooth = MockBluetoothService()
        let scaleService = MockScaleService()
        scaleService.deleteDeviceError = NSError(domain: "ScaleSettingsStoreTests", code: 1)
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), notification: notification, scaleService: scaleService, bluetooth: bluetooth)
        var didSucceed = false

        store.handleScaleDelete(scaleId: "scale-1") {
            didSucceed = true
        }
        notification.alertData?.buttons.first?.action(nil)
        await waitUntil(timeoutNanoseconds: 2_000_000_000) {
            scaleService.deleteDeviceCalls == 1 &&
            bluetooth.resyncAndScanCalls == 1 &&
            notification.dismissLoaderCalls == 1
        }

        #expect(scaleService.deleteDeviceCalls == 1)
        #expect(scaleService.pushLocalChangesToServerCalls == 0)
        #expect(scaleService.syncAllScalesWithRemoteCalls == 0)
        #expect(bluetooth.resyncAndScanCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(didSucceed == false)
    }

    @Test("setSessionImpedance success updates session flag")
    func setSessionImpedanceSuccessUpdatesFlag() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateSettingResult = .success(())
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), bluetooth: bluetooth)

        await store.setSessionImpedance(true)

        #expect(bluetooth.updateSettingCalls == 1)
        #expect(store.isImpedanceSwitchedOnForSession == true)
        #expect(bluetooth.lastUpdateSettings.first?.key == "SESSION_IMPEDANCE")
    }

    @Test("setSessionImpedance returns early when scale is disconnected")
    func setSessionImpedanceReturnsEarlyWhenDisconnected() async {
        let scale = makeR4Scale()
        scale.isConnected = false
        let bluetooth = MockBluetoothService()
        let (store, _, _, _, _) = makeSUT(scale: scale, bluetooth: bluetooth)

        await store.setSessionImpedance(true)

        #expect(bluetooth.updateSettingCalls == 0)
        #expect(store.isImpedanceSwitchedOnForSession == false)
    }

    @Test("setSessionImpedance failure leaves session flag unchanged")
    func setSessionImpedanceFailureLeavesSessionFlagUnchanged() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateSettingResult = .failure(.notImplemented)
        let (store, _, _, _, _) = makeSUT(scale: makeR4Scale(), bluetooth: bluetooth)

        await store.setSessionImpedance(true)

        #expect(bluetooth.updateSettingCalls == 1)
        #expect(store.isImpedanceSwitchedOnForSession == false)
    }

    private func makeSUT(
        scale: Device,
        notification: MockNotificationHelperService? = nil,
        scaleService: MockScaleService? = nil,
        bluetooth: MockBluetoothService? = nil,
        logger: MockLoggerService? = nil,
        account: MockAccountService? = nil,
        permissions: MockPermissionsService? = nil
    ) -> (ScaleSettingsStore, MockNotificationHelperService, MockScaleService, MockBluetoothService, MockPermissionsService) { // swiftlint:disable:this large_tuple
        let notification = notification ?? MockNotificationHelperService()
        let scaleService = scaleService ?? MockScaleService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let logger = logger ?? MockLoggerService()
        let account = account ?? MockAccountService()
        let permissions = permissions ?? MockPermissionsService()
        if account.activeAccount == nil {
            account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "scale@example.com", firstName: "Owner", isActiveAccount: true)
        }
        if permissions.permissions == nil {
            permissions.setPermissions([.BLUETOOTH_SWITCH: .ENABLED])
        }

        return (
            ScaleSettingsStore(
                scale: scale,
                notificationService: notification,
                scaleService: scaleService,
                bluetoothService: bluetooth,
                logger: logger,
                accountService: account,
                permissionsService: permissions
            ),
            notification,
            scaleService,
            bluetooth,
            permissions
        )
    }

    private func makeR4Scale(preferenceSynced: Bool = true) -> Device {
        let scale = ScaleTestFixtures.makeDevice(id: "scale-1", accountId: "acct-1", displayName: "Primary Scale")
        scale.isConnected = true
        scale.isWifiConfigured = true
        scale.broadcastIdString = "40E20100"
        scale.bathScale = BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        scale.r4ScalePreference?.isSynced = preferenceSynced
        scale.r4ScalePreference?.shouldMeasureImpedance = true
        scale.r4ScalePreference?.shouldMeasurePulse = false
        return scale
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }
}
