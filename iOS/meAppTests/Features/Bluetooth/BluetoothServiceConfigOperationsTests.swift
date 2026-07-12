import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

// Split out of BluetoothServiceCoreOperationsTests to keep each suite under the
// SwiftLint file_length / type_body_length limits. This suite covers Wi-Fi,
// live-measurement, firmware, settings, R4 profile, account/user-list, and
// utility (withTimeout) operations; device lifecycle (scan/pair/add/delete)
// stays in BluetoothServiceCoreOperationsTests.
@Suite(.serialized)
@MainActor
struct BluetoothServiceConfigOperationsTests {

    @Test("getWifiList maps sdk wifi response")
    func getWifiListSuccessMapsResponse() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "wifi-list-1", broadcastIdString: "WIFI11", isConnected: true)

        let result = await sut.getWifiList(broadcastId: device.broadcastIdString ?? "")

        guard case .success(let wifiList) = result else {
            Issue.record("Expected getWifiList to succeed")
            return
        }

        #expect(wifiList.count == 1)
        #expect(wifiList.first?.ssid == "Home WiFi")
        #expect(wifiList.first?.macAddress == "AA:BB:CC")
        #expect(sdk.wifiListDevices.count == 1)
    }

    @Test("setupWifi maps sdk response")
    func setupWifiSuccessMapsResponse() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "wifi-setup-1", broadcastIdString: "SET11", isConnected: true)

        let result = await sut.setupWifi(broadcastId: device.broadcastIdString ?? "", config: WifiConfig(ssid: "Office", password: "secret"))

        guard case .success(let response) = result else {
            Issue.record("Expected setupWifi to succeed")
            return
        }

        #expect(response == WifiSetupResponse(wifiState: "CONNECTED", errorCode: nil))
        #expect(sdk.setupWifiCalls.count == 1)
        #expect(sdk.setupWifiInputs.first?.ssid == "Office")
        #expect(sdk.setupWifiInputs.first?.password == "secret")
    }

    @Test("cancelWifi delegates to sdk")
    func cancelWifiSuccessDelegatesToSDK() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "wifi-cancel-1", broadcastIdString: "CAN11", isConnected: true)

        let result = await sut.cancelWifi(broadcastId: device.broadcastIdString ?? "")

        guard case .success = result else {
            Issue.record("Expected cancelWifi to succeed")
            return
        }

        #expect(sdk.cancelledWifiDevices.count == 1)
        #expect(sdk.cancelledWifiDevices.first?.broadcastId == "CAN11")
    }

    @Test("getConnectedWifiSSID returns sdk ssid")
    func getConnectedWifiSSIDSuccessReturnsSSID() async {
        let sdk = MockBluetoothSDKClient()
        sdk.getConnectedWifiSSIDResult = "Office WiFi"
        let sut = makeSUT(sdk: sdk)

        let result = await sut.getConnectedWifiSSID(broadcastId: "SSID11")

        guard case .success(let ssid) = result else {
            Issue.record("Expected getConnectedWifiSSID to succeed")
            return
        }

        #expect(ssid == "Office WiFi")
        #expect(sdk.connectedWifiSSIDRequests.count == 1)
    }

    @Test("getWifiMacAddress returns sdk wifi mac")
    func getWifiMacAddressSuccessReturnsMac() async {
        let sdk = MockBluetoothSDKClient()
        sdk.getWifiMacAddressResult = "66:55:44:33:22:11"
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "wifi-mac-1", broadcastIdString: "MAC11", isConnected: true)

        let result = await sut.getWifiMacAddress(broadcastId: device.broadcastIdString ?? "")

        guard case .success(let mac) = result else {
            Issue.record("Expected getWifiMacAddress to succeed")
            return
        }

        #expect(mac == "66:55:44:33:22:11")
        #expect(sdk.wifiMacAddressDevices.count == 1)
    }

    @Test("live measurement operations delegate to sdk")
    func liveMeasurementOperationsDelegateToSDK() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "live-1", broadcastIdString: "LIVE11", isConnected: true)

        let startResult = await sut.startLiveMeasurement(broadcastId: device.broadcastIdString ?? "")
        let stopResult = await sut.stopLiveMeasurement(broadcastId: device.broadcastIdString ?? "")

        guard case .success = startResult else {
            Issue.record("Expected startLiveMeasurement to succeed")
            return
        }
        guard case .success = stopResult else {
            Issue.record("Expected stopLiveMeasurement to succeed")
            return
        }

        #expect(sdk.startLiveMeasurementDevices.count == 1)
        #expect(sdk.stopLiveMeasurementDevices.count == 1)
    }

    @Test("updateSetting converts settings and delegates to sdk")
    func updateSettingSuccessMapsSettings() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "setting-1", broadcastIdString: "SETTING11", isConnected: true)

        let result = await sut.updateSetting(
            broadcastId: device.broadcastIdString ?? "",
            settings: [DeviceSetting(key: "SESSION_IMPEDANCE", value: .bool(true))]
        )

        guard case .success = result else {
            Issue.record("Expected updateSetting to succeed")
            return
        }

        #expect(sdk.updateSettingCalls.count == 1)
        #expect(sdk.updateSettingCalls.first?.settings.count == 1)
        #expect(sdk.updateSettingCalls.first?.settings.first?.key == .SESSION_IMPEDANCE)
    }

    @Test("updateFirmware starts sdk update and emits initial progress")
    func updateFirmwareSuccessEmitsInitialProgress() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "firmware-1", broadcastIdString: "FW11", isConnected: true)
        var received: [FirmwareUpdateStatus] = []
        let cancellable = sut.firmwareUpdateProgressPublisher.sink { received.append($0) }
        defer { cancellable.cancel() }

        let result = await sut.updateFirmware(broadcastId: device.broadcastIdString ?? "", timestamp: 12345)

        guard case .success = result else {
            Issue.record("Expected updateFirmware to succeed")
            return
        }

        #expect(sdk.firmwareUpdateCalls.count == 1)
        #expect(sdk.firmwareUpdateCalls.first?.timestamp == 12345)
        #expect(received == [FirmwareUpdateStatus(progress: 0.0, isComplete: false)])
    }

    @Test("clearData maps clear type and delegates to sdk")
    func clearDataSuccessMapsType() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "clear-1", broadcastIdString: "CLEAR11", isConnected: true)

        let result = await sut.clearData(broadcastId: device.broadcastIdString ?? "", dataType: .wifi)

        guard case .success = result else {
            Issue.record("Expected clearData to succeed")
            return
        }

        #expect(sdk.clearDataCalls.count == 1)
        #expect(sdk.clearDataCalls.first?.type == .WIFI)
    }

    @Test("updateUserProfileForR4Scales sends profile to sdk")
    func updateUserProfileForR4ScalesSuccess() async {
        let sdk = MockBluetoothSDKClient()
        let entry = MockEntryService()
        let latestEntry = HealthKitTestFixtures.makeEntry(accountId: "acct-profile", weight: 730)
        latestEntry.scaleEntry = BathScaleEntry(weight: 730)
        entry.latestEntry = latestEntry
        let sut = makeSUT(entry: entry, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-profile", email: "profile@example.com", isLoggedIn: true, isActiveAccount: true)
        let r4Device = makeDevice(
            id: "r4-profile",
            broadcastIdString: "ABC123",
            isConnected: true,
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        sut.bluetoothScales = [r4Device.toSnapshot(isConnected: true)]

        let result = await sut.updateUserProfileForR4Scales()

        guard case .success(let updatedIds) = result else {
            Issue.record("Expected updateUserProfileForR4Scales to succeed")
            return
        }

        #expect(updatedIds == ["ABC123"])
        #expect(sdk.updateProfileRequests.count == 1)
        #expect(sdk.updateProfileRequests.first?.name == sut.activeAccount?.firstName ?? "User")
    }

    @Test("updateAccount maps attached preference and sdk response")
    func updateAccountSuccessUsesAttachedPreference() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        let device = makeDevice(id: "account-1", broadcastIdString: "ACCOUNT11", isConnected: true)
        let attachedPreference = makePreference(id: device.id)
        attachedPreference.displayName = "Attached User"
        scale.attachedPreferences[device.id] = attachedPreference
        sut.bluetoothScales = [device.toSnapshot()]

        let result = await sut.updateAccount(broadcastId: device.broadcastIdString ?? "")

        guard case .success(let response) = result else {
            Issue.record("Expected updateAccount to succeed")
            return
        }

        #expect(response == .creationCompleted)
        #expect(sdk.updatedAccountDevices.count == 1)
        #expect(sdk.updatedAccountDevices.first?.preference?.displayName == "Attached User")
    }

    @Test("getScaleUserList maps sdk users")
    func getScaleUserListSuccessMapsUsers() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "users-1", broadcastIdString: "USER11", isConnected: true)
        device.sku = "0375" // weight scale; isBpmDevice() must be false so the user-list fetch runs
        sut.bluetoothScales = [device.toSnapshot(isConnected: true)]

        let result = await sut.getScaleUserList(broadcastId: device.broadcastIdString ?? "")

        guard case .success(let users) = result else {
            Issue.record("Expected getScaleUserList to succeed")
            return
        }

        #expect(users.count == 1)
        #expect(users.first?.name == "Scale User")
        #expect(users.first?.token == "user-token")
        #expect(sdk.userListRequests.count == 1)
    }

    @Test("confirmSmartPair and updateAccount reject invalid broadcast ids")
    func invalidBroadcastOperationsFailConsistently() async {
        let sut = makeSUT()
        let invalidDevice = makeDevice(id: "invalid-1", broadcastIdString: nil, isConnected: true)

        expectInvalidBroadcast(await sut.confirmSmartPair(device: invalidDevice, token: "token", displayName: "User", userNumber: 1))
        expectInvalidBroadcast(await sut.updateAccount(broadcastId: ""))
    }

    @Test("updateUserProfileForR4Scales guards repeated in-flight calls")
    func updateUserProfileForR4ScalesRejectsConcurrentRuns() async {
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger)
        sut.isUpdatingR4Profile = true

        let result = await sut.updateUserProfileForR4Scales()

        switch result {
        case .success:
            Issue.record("Expected updateUserProfileForR4Scales to fail")
        case .failure(let error):
            guard case .updateProfileFailed = error else {
                Issue.record("Expected updateProfileFailed, got \(error)")
                return
            }
        }

        #expect(logger.messages.contains { $0.contains("already in progress") })
    }

    @Test("updateUserProfileForR4Scales without active account returns noActiveAccount")
    func updateUserProfileForR4ScalesWithoutActiveAccountFails() async {
        let sut = makeSUT()
        sut.activeAccount = nil

        let result = await sut.updateUserProfileForR4Scales()

        switch result {
        case .success:
            Issue.record("Expected updateUserProfileForR4Scales to fail")
        case .failure(let error):
            guard case .noActiveAccount = error else {
                Issue.record("Expected noActiveAccount, got \(error)")
                return
            }
        }
    }

    @Test("getScaleUserList without connection returns deviceNotConnected")
    func getScaleUserListWithoutConnectionFails() async {
        let sut = makeSUT()
        // Tracked weight scale (non-BPM) that is not connected: isBpmDevice() is false, so the
        // flow reaches the connection guard rather than failing closed as a BPM monitor.
        let device = makeDevice(id: "offline-user-1", broadcastIdString: "ABC123", isConnected: false)
        device.sku = "0375"
        sut.bluetoothScales = [device.toSnapshot(isConnected: false)]

        let result = await sut.getScaleUserList(broadcastId: "ABC123")

        switch result {
        case .success:
            Issue.record("Expected getScaleUserList to fail")
        case .failure(let error):
            guard case .deviceNotConnected = error else {
                Issue.record("Expected deviceNotConnected, got \(error)")
                return
            }
        }
    }

    @Test("getScaleUserList skips BPM monitors (SDK getUsers crashes for BPM) and never calls the SDK")
    func getScaleUserListSkipsBpmDevices() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "bpm-1", broadcastIdString: "BPM663", isConnected: true)
        device.sku = "0663" // A6 BPM monitor
        sut.bluetoothScales = [device.toSnapshot(isConnected: true)]

        let result = await sut.getScaleUserList(broadcastId: device.broadcastIdString ?? "", skipConnectionCheck: true)

        switch result {
        case .success:
            Issue.record("Expected getScaleUserList to fail for a BPM monitor")
        case .failure(let error):
            guard case .notImplemented = error else {
                Issue.record("Expected notImplemented, got \(error)")
                return
            }
        }
        // Critically, the crashing SDK getUsers call must not have been made.
        #expect(sdk.userListRequests.isEmpty)
    }

    @Test("withTimeout returns successful result before timeout expires")
    func withTimeoutReturnsBeforeDeadline() async throws {
        let sut = makeSUT()

        let result = try await sut.withTimeout(seconds: 1) {
            "done"
        }

        #expect(result == "done")
    }

    @Test("withTimeout throws timeout for stalled operations")
    func withTimeoutThrowsTimeoutForStalledOperation() async {
        let sut = makeSUT()

        do {
            _ = try await sut.withTimeout(seconds: 1) {
                try await Task.sleep(nanoseconds: 2_000_000_000)
                return "late"
            }
            Issue.record("Expected timeout")
        } catch {
            guard case .timeout = error as? BluetoothServiceError else {
                Issue.record("Expected timeout, got \(error)")
                return
            }
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        account: MockAccountService? = nil,
        scale: MockScaleService? = nil,
        entry: MockEntryService? = nil,
        logger: MockLoggerService? = nil,
        discovery: MockBLEDiscoveryManager? = nil,
        sdk: MockBluetoothSDKClient? = nil,
        notification: MockNotificationHelperService? = nil
    ) -> BluetoothService {
        BluetoothService(
            accountService: account ?? MockAccountService(),
            deviceService: scale ?? MockScaleService(),
            entryService: entry ?? MockEntryService(),
            babyService: MockBabyService(),
            logger: logger ?? MockLoggerService(),
            discoveryManager: discovery ?? MockBLEDiscoveryManager(),
            ggBleSDK: sdk ?? MockBluetoothSDKClient(),
            notificationService: notification ?? MockNotificationHelperService()
        )
    }

    private func makeDevice(
        id: String = "device-1",
        broadcastIdString: String? = "ABC123",
        isConnected: Bool? = true,
        bathScale: BathScale? = nil
    ) -> Device {
        BluetoothTestFixtures.makeDevice(
            id: id,
            broadcastIdString: broadcastIdString,
            isConnected: isConnected,
            bathScale: bathScale
        )
    }

    private func makePreference(id: String) -> R4ScalePreference {
        R4ScalePreference(
            scaleId: id,
            displayName: "Test User",
            displayMetrics: ["weight"],
            shouldFactoryReset: false,
            shouldMeasureImpedance: true,
            shouldMeasurePulse: true,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
    }

    private func expectInvalidBroadcast<T>(_ result: Result<T, BluetoothServiceError>) {
        switch result {
        case .success:
            Issue.record("Expected invalidBroadcastId")
        case .failure(let error):
            guard case .invalidBroadcastId = error else {
                Issue.record("Expected invalidBroadcastId, got \(error)")
                return
            }
        }
    }
}
