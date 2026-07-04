// swiftlint:disable file_length
import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
// swiftlint:disable:next type_body_length
struct BluetoothServiceCoreOperationsTests {

    @Test("scan with no active account returns early")
    func scanWithoutActiveAccountReturnsEarly() async {
        let sut = makeSUT()
        sut.activeAccount = nil

        await sut.scan()

        #expect(sut.isSmartScanStarted == false)
    }

    @Test("resyncAndScan success clears transient discovery state and preserves scale-service sequencing")
    func resyncAndScanSuccessClearsStateAndOrdersScaleSync() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        sut.bluetoothScales = [
            makeDevice(id: "paired-1", broadcastIdString: "AA11").toSnapshot(),
            makeDevice(id: "paired-2", broadcastIdString: "BB22").toSnapshot()
        ]
        sut.skipDevices = ["SKIP-1", "SKIP-2"]
        sut.reconnectAlertSkippedDevices = ["RECONNECT-1"]

        let result = await sut.resyncAndScan()

        guard case .success = result else {
            Issue.record("Expected resyncAndScan to succeed")
            return
        }

        #expect(sut.skipDevices.isEmpty)
        #expect(sut.reconnectAlertSkippedDevices.isEmpty)
        #expect(scale.syncDevicesCalls == 1)
        #expect(scale.callSequence == ["updateAllScalesStatus", "syncDevices"])
    }

    @Test("resyncAndScan wraps unexpected failures and avoids partial cleanup")
    func resyncAndScanUnexpectedFailurePreservesExistingTransientState() async {
        let scale = MockScaleService()
        scale.updateAllScalesStatusError = CoreOpsTestError.unexpected
        let sut = makeSUT(scale: scale)
        sut.skipDevices = ["SKIP-1"]
        sut.reconnectAlertSkippedDevices = ["RECONNECT-1"]

        let result = await sut.resyncAndScan()

        switch result {
        case .success:
            Issue.record("Expected resyncAndScan to fail")
        case .failure(let error):
            guard case .resyncFailed = error else {
                Issue.record("Expected resyncFailed, got \(error)")
                return
            }
        }

        #expect(sut.skipDevices == ["SKIP-1"])
        #expect(sut.reconnectAlertSkippedDevices == ["RECONNECT-1"])
        #expect(scale.callSequence == ["updateAllScalesStatus"])
    }

    @Test("resyncAndScan passes through BluetoothServiceError without wrapping")
    func resyncAndScanPassesThroughServiceErrors() async {
        let scale = MockScaleService()
        scale.updateAllScalesStatusError = BluetoothServiceError.noActiveAccount
        let sut = makeSUT(scale: scale)

        let result = await sut.resyncAndScan()

        switch result {
        case .success:
            Issue.record("Expected resyncAndScan to fail")
        case .failure(let error):
            guard case .noActiveAccount = error else {
                Issue.record("Expected noActiveAccount, got \(error)")
                return
            }
        }
    }

    @Test("disconnectConnectedScales disconnects only connected devices and clears skip state afterwards")
    func disconnectConnectedScalesCleansConnectedDevicesAndSkipState() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        let connectedOne = makeDevice(id: "connected-1", broadcastIdString: "AA11", isConnected: true)
        let connectedTwo = makeDevice(id: "connected-2", broadcastIdString: "BB22", isConnected: true)
        let disconnected = makeDevice(id: "disconnected-1", broadcastIdString: "CC33", isConnected: false)
        sut.bluetoothScales = [
            connectedOne.toSnapshot(isConnected: true),
            connectedTwo.toSnapshot(isConnected: true),
            disconnected.toSnapshot(isConnected: false)
        ]
        sut.skipDevices = ["STALE-ID"]

        await sut.disconnectConnectedScales()

        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 2)
        #expect(sut.skipDevices.isEmpty)
        #expect(sut.blockedBroadcastIds.contains("AA11"))
        #expect(sut.blockedBroadcastIds.contains("BB22"))
        #expect(sut.blockedBroadcastIds.contains("CC33") == false)
    }

    @Test("disconnectDevice repeated calls do not duplicate skip state or leak unblock tasks")
    func disconnectDeviceRepeatedCallsAreSafe() async {
        let sut = makeSUT()

        _ = await sut.disconnectDevice(broadcastId: "AA11")
        let firstTask = sut.unblockTasks["AA11"]
        _ = await sut.disconnectDevice(broadcastId: "AA11")

        #expect(sut.skipDevices == ["AA11"])
        #expect(sut.unblockTasks.count == 1)
        #expect(sut.unblockTasks["AA11"] != nil)
        #expect(firstTask != nil)
    }

    @Test("deleteCurrentUserFromScaleIfPossible returns deviceNotFound when scale is not in bluetooth scales")
    func deleteCurrentUserFromScaleIfPossibleDisconnectedDevice() async {
        let sut = makeSUT()
        let disconnectedDevice = makeDevice(id: "offline-1", broadcastIdString: "AA11", isConnected: false)

        let result = await sut.deleteCurrentUserFromScaleIfPossible(broadcastId: disconnectedDevice.broadcastIdString ?? "", disconnect: true)

        switch result {
        case .success:
            Issue.record("Expected deleteCurrentUserFromScaleIfPossible to fail")
        case .failure(let error):
            guard case .deviceNotFound = error else {
                Issue.record("Expected deviceNotFound, got \(error)")
                return
            }
        }

        #expect(sut.blockedBroadcastIds.isEmpty)
        #expect(sut.skipDevices.isEmpty)
    }

    @Test("deleteCurrentUserFromScaleIfPossible returns timeout when task is cancelled before persisted-token delete")
    func deleteCurrentUserFromScaleIfPossibleCancelledWithPersistedToken() async {
        let sut = makeSUT()
        let device = makeDevice(id: "cancelled-1", broadcastIdString: "AA11", isConnected: true)
        device.token = "persisted-token"
        sut.bluetoothScales = [device.toSnapshot()]

        let task = Task { @MainActor in
            await sut.deleteCurrentUserFromScaleIfPossible(broadcastId: device.broadcastIdString ?? "", disconnect: false)
        }
        task.cancel()
        let result = await task.value

        switch result {
        case .success:
            Issue.record("Expected cancellation timeout")
        case .failure(let error):
            guard case .timeout = error else {
                Issue.record("Expected timeout, got \(error)")
                return
            }
        }
    }

    @Test("deleteCurrentUserFromScaleIfPossible returns timeout when task is cancelled before user list lookup")
    func deleteCurrentUserFromScaleIfPossibleCancelledBeforeUserListLookup() async {
        let sut = makeSUT()
        let device = makeDevice(id: "cancelled-2", broadcastIdString: "BB22", isConnected: true)
        device.token = nil

        let task = Task { @MainActor in
            await sut.deleteCurrentUserFromScaleIfPossible(broadcastId: device.broadcastIdString ?? "", disconnect: false)
        }
        task.cancel()
        let result = await task.value

        switch result {
        case .success:
            Issue.record("Expected cancellation timeout")
        case .failure(let error):
            guard case .timeout = error else {
                Issue.record("Expected timeout, got \(error)")
                return
            }
        }
    }

    @Test("addNewDevice with metadata provided persists device and syncs without extra SDK lookups")
    func addNewDeviceWithProvidedMetadataPersistsAndSyncs() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "u@example.com", isLoggedIn: true, isActiveAccount: true)

        let metadata = DeviceMetaData(modelNumber: "Model-1", serialNumber: "Serial-1")
        let device = makeDevice(
            id: "new-device-1",
            broadcastIdString: "AA11",
            bathScale: BathScale(scaleType: DeviceSourceType.bluetoothScale.rawValue, bodyComp: true)
        )
        device.nickname = nil
        device.password = 1234

        let result = await sut.addNewDevice(device, metaData: metadata, true)

        guard case .success(let savedDevice) = result else {
            Issue.record("Expected addNewDevice to succeed")
            return
        }

        #expect(scale.callSequence == ["createDevice", "syncDevices"])
        #expect(scale.createDeviceCalls == 1)
        #expect(scale.lastCreatedDevice?.accountId == "acct-1")
        #expect(scale.lastCreatedDevice?.nickname == "Bluetooth Smart Scale")
        #expect(scale.lastCreatedDevice?.createdAt != nil)
        #expect(scale.lastCreatedDevice?.metaData?.modelNumber == "Model-1")
        #expect(savedDevice.metaData?.serialNumber == "Serial-1")
    }

    @Test("addNewDevice without active account returns noActiveAccount")
    func addNewDeviceWithoutActiveAccountFails() async {
        let sut = makeSUT()
        sut.activeAccount = nil

        let result = await sut.addNewDevice(makeDevice(), metaData: DeviceMetaData(modelNumber: "Model-1"))

        switch result {
        case .success:
            Issue.record("Expected addNewDevice to fail")
        case .failure(let error):
            guard case .noActiveAccount = error else {
                Issue.record("Expected noActiveAccount, got \(error)")
                return
            }
        }
    }

    @Test("addNewDevice wraps unexpected create failures")
    func addNewDeviceWrapsUnexpectedCreateFailures() async {
        let scale = MockScaleService()
        scale.createDeviceError = CoreOpsTestError.unexpected
        let sut = makeSUT(scale: scale)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-2", email: "u2@example.com", isLoggedIn: true, isActiveAccount: true)

        let result = await sut.addNewDevice(makeDevice(id: "new-device-2"), metaData: DeviceMetaData(modelNumber: "Model-2"))

        switch result {
        case .success:
            Issue.record("Expected addNewDevice to fail")
        case .failure(let error):
            guard case .updateProfileFailed = error else {
                Issue.record("Expected updateProfileFailed, got \(error)")
                return
            }
        }

        #expect(scale.callSequence == ["createDevice"])
    }

    @Test("addNewDevice passes through BluetoothServiceError from createDevice")
    func addNewDevicePassesThroughServiceErrors() async {
        let scale = MockScaleService()
        scale.createDeviceError = BluetoothServiceError.invalidDeviceState
        let sut = makeSUT(scale: scale)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-3", email: "u3@example.com", isLoggedIn: true, isActiveAccount: true)

        let result = await sut.addNewDevice(makeDevice(id: "new-device-3"), metaData: DeviceMetaData(modelNumber: "Model-3"))

        switch result {
        case .success:
            Issue.record("Expected addNewDevice to fail")
        case .failure(let error):
            guard case .invalidDeviceState = error else {
                Issue.record("Expected invalidDeviceState, got \(error)")
                return
            }
        }
    }

    @Test("addNewDevice skips bluetooth info lookup for non-bluetooth scale types")
    func addNewDeviceSkipsExtraLookupForNonBluetoothTypes() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-4", email: "u4@example.com", isLoggedIn: true, isActiveAccount: true)

        let device = makeDevice(
            id: "new-device-4",
            broadcastIdString: "CC33",
            bathScale: BathScale(scaleType: DeviceSourceType.wifi.rawValue, bodyComp: true)
        )

        let result = await sut.addNewDevice(device, metaData: nil)

        guard case .success(let savedDevice) = result else {
            Issue.record("Expected addNewDevice to succeed")
            return
        }

        #expect(scale.callSequence == ["createDevice", "syncDevices"])
        #expect(savedDevice.metaData == nil)
        #expect(savedDevice.wifiMac == nil)
    }

    @Test("scan with active account starts smart scan")
    func scanWithActiveAccountStartsSmartScan() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-scan", email: "scan@example.com", isLoggedIn: true, isActiveAccount: true)

        await sut.scan()

        #expect(sut.isSmartScanStarted == true)
        #expect(sdk.scannedAppTypes.count == 1)
        #expect(sdk.scannedProfiles.count == 1)
    }

    @Test("addNewDevice fetches device info and wifi mac for R4 scales")
    func addNewDeviceR4FetchesDeviceInfoAndWifiMac() async {
        let scale = MockScaleService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-r4", email: "r4@example.com", isLoggedIn: true, isActiveAccount: true)

        let device = makeDevice(
            id: "r4-device",
            broadcastIdString: "R4AA11",
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )

        let result = await sut.addNewDevice(device, metaData: nil)

        guard case .success(let savedDevice) = result else {
            Issue.record("Expected addNewDevice to succeed")
            return
        }

        #expect(sdk.deviceInfoRequests.count == 1)
        #expect(sdk.wifiMacAddressDevices.count == 1)
        #expect(savedDevice.metaData?.modelNumber == "Model-R4")
        #expect(savedDevice.metaData?.serialNumber == "Serial-123")
        #expect(savedDevice.metaData?.manufacturerName == "Weight Gurus")
        #expect(savedDevice.wifiMac == "11:22:33:44:55:66")
    }

    @Test("confirmSmartPair maps response and configures sdk device payload")
    func confirmSmartPairSuccessMapsResponse() async {
        let sdk = MockBluetoothSDKClient()
        sdk.confirmPairResult = .CREATION_COMPLETED
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "pair-1", broadcastIdString: "PAIR11", isConnected: true)

        let result = await sut.confirmSmartPair(device: device, token: "pair-token", displayName: "Pair User", userNumber: 7)

        guard case .success(let response) = result else {
            Issue.record("Expected confirmSmartPair to succeed")
            return
        }

        #expect(response == .creationCompleted)
        #expect(sdk.confirmedDevices.count == 1)
        #expect(sdk.confirmedDevices.first?.token == "pair-token")
        #expect(sdk.confirmedDevices.first?.userNumber == 7)
        #expect(sdk.confirmedDevices.first?.preference?.displayName == "Pair User")
    }

    @Test("deleteDevice maps sdk deletion response and disconnect flag")
    func deleteDeviceSuccessMapsResponse() async {
        let sdk = MockBluetoothSDKClient()
        sdk.deleteUserResult = .SUCCESS
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "delete-1", broadcastIdString: "DEL11", isConnected: true)
        device.token = "del-token"
        sut.bluetoothScales = [device.toSnapshot()]

        let result = await sut.deleteDevice(broadcastId: device.broadcastIdString ?? "", disconnect: true)

        guard case .success(let response) = result else {
            Issue.record("Expected deleteDevice to succeed")
            return
        }

        #expect(response == .success)
        #expect(sdk.deletedDevices.count == 1)
        #expect(sdk.deletedDevices.first?.disconnect == true)
        #expect(sdk.deletedDevices.first?.device.broadcastId == "DEL11")
    }

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

    // MARK: - Helpers

    func makeSUT(
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

    func makeDevice(
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

    func makePreference(id: String) -> R4ScalePreference {
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

    func expectInvalidBroadcast<T>(_ result: Result<T, BluetoothServiceError>) {
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

private enum CoreOpsTestError: Error {
    case unexpected
}
