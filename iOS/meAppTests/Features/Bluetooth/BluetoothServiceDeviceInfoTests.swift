import Combine
import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BluetoothServiceDeviceInfoTests {

    // MARK: - getDeviceInfo

    @Test("getDeviceInfo returns failure when device not connected and skipConnectionCheck is false")
    func getDeviceInfoNotConnected() async {
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger)
        let device = makeDevice(isConnected: false)

        let result = await sut.getDeviceInfo(for: device)

        guard case .failure(let error) = result else {
            Issue.record("Expected failure")
            return
        }
        guard case .deviceNotConnected = error else {
            Issue.record("Expected deviceNotConnected, got \(error)")
            return
        }
    }

    @Test("getDeviceInfo succeeds when device is connected")
    func getDeviceInfoConnected() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.getDeviceInfo(for: device)

        guard case .success(let info) = result else {
            Issue.record("Expected success, got \(result)")
            return
        }
        #expect(info.deviceName == "Scale")
        #expect(info.broadcastIdString == "ABC123")
    }

    @Test("getDeviceInfo succeeds with skipConnectionCheck even when not connected")
    func getDeviceInfoSkipConnectionCheck() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: false)

        let result = await sut.getDeviceInfo(for: device, skipConnectionCheck: true)

        guard case .success = result else {
            Issue.record("Expected success, got \(result)")
            return
        }
    }

    @Test("getDeviceInfo returns invalidBroadcastId when broadcastIdString is nil")
    func getDeviceInfoNilBroadcastId() async {
        let sut = makeSUT()
        let device = makeDevice(broadcastIdString: nil, isConnected: true)

        let result = await sut.getDeviceInfo(for: device, skipConnectionCheck: true)

        guard case .failure(let error) = result else {
            Issue.record("Expected failure")
            return
        }
        guard case .invalidBroadcastId = error else {
            Issue.record("Expected invalidBroadcastId, got \(error)")
            return
        }
    }

    @Test("getDeviceInfo returns deviceNotConnected when SDK returns nil details")
    func getDeviceInfoNilDetails() async {
        let sdk = MockBluetoothSDKClient()
        sdk.getDeviceInfoResult = nil
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.getDeviceInfo(for: device)

        guard case .failure(let error) = result else {
            Issue.record("Expected failure")
            return
        }
        guard case .deviceNotConnected = error else {
            Issue.record("Expected deviceNotConnected, got \(error)")
            return
        }
    }

    @Test("getDeviceInfo returns failure when SDK throws")
    func getDeviceInfoSDKError() async {
        let sdk = MockBluetoothSDKClient()
        sdk.getDeviceInfoError = DeviceInfoTestError.sdkFailure
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger, sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.getDeviceInfo(for: device)

        guard case .failure = result else {
            Issue.record("Expected failure")
            return
        }
        #expect(logger.messages.contains { $0.contains("Failed to get device info") })
    }

    @Test("getDeviceInfo maps all GGDeviceDetails fields to DeviceInfo")
    func getDeviceInfoMapsFields() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.getDeviceInfo(for: device)

        guard case .success(let info) = result else {
            Issue.record("Expected success")
            return
        }
        // Verify key fields from the mock's default getDeviceInfoResult
        #expect(info.manufacturerName == "Weight Gurus")
        #expect(info.modelNumber == "Model-R4")
        #expect(info.serialNumber == "Serial-123")
        #expect(info.firmwareRevision == "FW-1")
        #expect(info.protocolType == "R4")
        #expect(info.batteryLevel == 90)
    }

    @Test("getDeviceInfo tracks device info request on SDK")
    func getDeviceInfoTracksRequest() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        _ = await sut.getDeviceInfo(for: device)

        #expect(sdk.deviceInfoRequests.count == 1)
    }

    // MARK: - getDeviceLogs

    @Test("getDeviceLogs returns success with log entries")
    func getDeviceLogsSuccess() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.getDeviceLogs(for: device)

        guard case .success(let logs) = result else {
            Issue.record("Expected success")
            return
        }
        #expect(logs.logs.count == 1)
        #expect(logs.logs.first?.macAddress == "AA:BB:CC")
        #expect(logs.logs.first?.log == "entry")
    }

    @Test("getDeviceLogs returns invalidBroadcastId when broadcastIdString is nil")
    func getDeviceLogsNilBroadcastId() async {
        let sut = makeSUT()
        let device = makeDevice(broadcastIdString: nil)

        let result = await sut.getDeviceLogs(for: device)

        guard case .failure(let error) = result else {
            Issue.record("Expected failure")
            return
        }
        guard case .invalidBroadcastId = error else {
            Issue.record("Expected invalidBroadcastId, got \(error)")
            return
        }
    }

    @Test("getDeviceLogs returns failure when SDK throws")
    func getDeviceLogsSDKError() async {
        let sdk = MockBluetoothSDKClient()
        sdk.getDeviceLogsError = DeviceInfoTestError.sdkFailure
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.getDeviceLogs(for: device)

        guard case .failure(let error) = result else {
            Issue.record("Expected failure")
            return
        }
        guard case .getDeviceLogsFailed = error else {
            Issue.record("Expected getDeviceLogsFailed, got \(error)")
            return
        }
    }

    // MARK: - getMeasurementLiveData

    @Test("getMeasurementLiveData returns success")
    func getMeasurementLiveDataSuccess() async {
        let sut = makeSUT()

        let result = await sut.getMeasurementLiveData(broadcastId: "LIVE-1")

        guard case .success(let data) = result else {
            Issue.record("Expected success")
            return
        }
        #expect(data.weight == 0)
    }

    @Test("getMeasurementLiveData returns failure when SDK throws")
    func getMeasurementLiveDataSDKError() async {
        let sdk = MockBluetoothSDKClient()
        sdk.getMeasurementLiveDataError = DeviceInfoTestError.sdkFailure
        let sut = makeSUT(sdk: sdk)

        let result = await sut.getMeasurementLiveData(broadcastId: "LIVE-2")

        guard case .failure = result else {
            Issue.record("Expected failure")
            return
        }
    }

    // MARK: - updateWeightOnlyMode

    @Test("updateWeightOnlyMode updates specific connected scale")
    func updateWeightOnlyModeSpecificScale() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(id: "wo-1", isConnected: true)

        let result = await sut.updateWeightOnlyMode(on: device)

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.updateSettingCalls.count == 1)
    }

    @Test("updateWeightOnlyMode with nil uses all connected bluetoothScales")
    func updateWeightOnlyModeAllConnected() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.bluetoothScales = [
            makeDevice(id: "c1", broadcastIdString: "BID-C1", isConnected: true),
            makeDevice(id: "c2", broadcastIdString: "BID-C2", isConnected: true),
            makeDevice(id: "d1", broadcastIdString: "BID-D1", isConnected: false)
        ]

        let result = await sut.updateWeightOnlyMode(on: nil)

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        // Only the 2 connected scales should be updated
        #expect(sdk.updateSettingCalls.count == 2)
    }

    @Test("updateWeightOnlyMode with nil and no connected scales does nothing")
    func updateWeightOnlyModeNoConnected() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.bluetoothScales = [
            makeDevice(id: "d1", broadcastIdString: "BID-1", isConnected: false)
        ]

        let result = await sut.updateWeightOnlyMode(on: nil)

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.updateSettingCalls.isEmpty)
    }

    @Test("updateWeightOnlyMode returns success even when SDK updateSetting fails")
    func updateWeightOnlyModeSDKFailure() async {
        let sdk = MockBluetoothSDKClient()
        sdk.updateSettingError = DeviceInfoTestError.sdkFailure
        let sut = makeSUT(sdk: sdk)
        let device = makeDevice(isConnected: true)

        let result = await sut.updateWeightOnlyMode(on: device)

        // updateWeightOnlyMode always returns .success
        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
    }

    // MARK: - clearScaleDiscoveredInfo

    @Test("clearScaleDiscoveredInfo clears both skip lists")
    func clearScaleDiscoveredInfo() {
        let sut = makeSUT()
        sut.skipDevices = ["A", "B"]
        sut.reconnectAlertSkippedDevices = ["C", "D"]

        sut.clearScaleDiscoveredInfo()

        #expect(sut.skipDevices.isEmpty)
        #expect(sut.reconnectAlertSkippedDevices.isEmpty)
    }

    @Test("clearScaleDiscoveredInfo is safe on already empty lists")
    func clearScaleDiscoveredInfoAlreadyEmpty() {
        let sut = makeSUT()

        sut.clearScaleDiscoveredInfo()

        #expect(sut.skipDevices.isEmpty)
        #expect(sut.reconnectAlertSkippedDevices.isEmpty)
    }

    // MARK: - disconnectConnectedScales

    @Test("disconnectConnectedScales disconnects all connected scales")
    func disconnectConnectedScalesAll() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.bluetoothScales = [
            makeDevice(id: "c1", broadcastIdString: "BID-C1", isConnected: true),
            makeDevice(id: "c2", broadcastIdString: "BID-C2", isConnected: true),
            makeDevice(id: "d1", broadcastIdString: "BID-D1", isConnected: false)
        ]

        await sut.disconnectConnectedScales()

        // 2 connected scales should be processed
        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 2)
        #expect(sdk.skippedDevices.count == 2)
    }

    @Test("disconnectConnectedScales clears skipDevices")
    func disconnectConnectedScalesClearsSkip() async {
        let sut = makeSUT()
        sut.skipDevices = ["OLD-1", "OLD-2"]
        sut.bluetoothScales = []

        await sut.disconnectConnectedScales()

        #expect(sut.skipDevices.isEmpty)
    }

    @Test("disconnectConnectedScales resets isWeighOnlyModeEnabledByOthers")
    func disconnectConnectedScalesResetsWeightOnly() async {
        let sut = makeSUT()
        let device = makeDevice(id: "wo-1", broadcastIdString: "WO-BID", isConnected: true)
        device.isWeighOnlyModeEnabledByOthers = true
        sut.bluetoothScales = [device]

        await sut.disconnectConnectedScales()

        #expect(device.isWeighOnlyModeEnabledByOthers == false)
    }

    @Test("disconnectConnectedScales skips devices without broadcastIdString")
    func disconnectConnectedScalesNilBroadcastId() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        let device = makeDevice(id: "no-bid", broadcastIdString: nil, isConnected: true)
        sut.bluetoothScales = [device]

        await sut.disconnectConnectedScales()

        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 0)
        #expect(sdk.skippedDevices.isEmpty)
    }

    // MARK: - deleteR4Scales

    @Test("deleteR4Scales deletes connected R4 scales")
    func deleteR4ScalesConnectedR4() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let logger = MockLoggerService()
        let sut = makeSUT(scale: scale, logger: logger, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let r4Device = makeDevice(
            id: "r4-1",
            broadcastIdString: "R4-BID",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        sut.bluetoothScales = [r4Device]

        let result = await sut.deleteR4Scales()

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.deletedDevices.count == 1)
        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 1)
        #expect(sdk.skippedDevices.contains { $0.broadcastId == "R4-BID" })
    }

    @Test("deleteR4Scales skips non-R4 scales")
    func deleteR4ScalesSkipsNonR4() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)

        let wifiDevice = makeDevice(
            id: "wifi-1",
            broadcastIdString: "WIFI-BID",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.wifi.rawValue, bodyComp: false)
        )
        sut.bluetoothScales = [wifiDevice]

        let result = await sut.deleteR4Scales()

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.deletedDevices.isEmpty)
    }

    @Test("deleteR4Scales skips disconnected R4 scales")
    func deleteR4ScalesSkipsDisconnected() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)

        let device = makeDevice(
            id: "r4-disc",
            broadcastIdString: "R4-DISC",
            isConnected: false,
            bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        sut.bluetoothScales = [device]

        let result = await sut.deleteR4Scales()

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.deletedDevices.isEmpty)
    }

    @Test("deleteR4Scales skips scales without bathScale")
    func deleteR4ScalesNoBathScale() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)

        let device = makeDevice(id: "no-bath", broadcastIdString: "NO-BATH", isConnected: true)
        sut.bluetoothScales = [device]

        let result = await sut.deleteR4Scales()

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.deletedDevices.isEmpty)
    }

    @Test("deleteR4Scales logs failure when SDK delete fails")
    func deleteR4ScalesDeleteFails() async {
        let sdk = MockBluetoothSDKClient()
        sdk.deleteUserError = DeviceInfoTestError.sdkFailure
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let device = makeDevice(
            id: "r4-fail",
            broadcastIdString: "R4-FAIL",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        sut.bluetoothScales = [device]

        let result = await sut.deleteR4Scales()

        // deleteR4Scales always returns success
        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(logger.messages.contains { $0.contains("Failed to delete R4 scale") })
    }

    @Test("deleteR4Scales resets isWeighOnlyModeEnabledByOthers before deleting")
    func deleteR4ScalesResetsWeightOnlyMode() async {
        let scale = MockScaleService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let device = makeDevice(
            id: "r4-wo",
            broadcastIdString: "R4-WO",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        device.isWeighOnlyModeEnabledByOthers = true
        sut.bluetoothScales = [device]

        _ = await sut.deleteR4Scales()

        #expect(device.isWeighOnlyModeEnabledByOthers == false)
        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 1)
    }

    @Test("deleteR4Scales returns success with empty bluetoothScales")
    func deleteR4ScalesEmptyList() async {
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger)
        sut.bluetoothScales = []

        let result = await sut.deleteR4Scales()

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(logger.messages.contains { $0.contains("Found 0 connected R4 scales to delete") })
    }

    @Test("deleteR4Scales handles multiple R4 scales")
    func deleteR4ScalesMultiple() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let r4a = makeDevice(
            id: "r4-a",
            broadcastIdString: "R4-A",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        let r4b = makeDevice(
            id: "r4-b",
            broadcastIdString: "R4-B",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        let nonR4 = makeDevice(
            id: "wifi-1",
            broadcastIdString: "WIFI-1",
            isConnected: true,
            bathScale: BathScale(scaleType: ScaleSourceType.wifi.rawValue, bodyComp: false)
        )
        sut.bluetoothScales = [r4a, r4b, nonR4]

        let result = await sut.deleteR4Scales()

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        // Only the 2 R4 scales should be deleted
        #expect(sdk.deletedDevices.count == 2)
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
            scaleService: scale ?? MockScaleService(),
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
}

private enum DeviceInfoTestError: Error {
    case sdkFailure
}
