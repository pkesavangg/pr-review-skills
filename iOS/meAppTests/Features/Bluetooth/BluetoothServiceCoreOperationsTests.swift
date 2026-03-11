import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BluetoothServiceCoreOperationsTests {

    @Test("resyncAndScan success clears transient discovery state and preserves scale-service sequencing")
    func resyncAndScanSuccessClearsStateAndOrdersScaleSync() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        sut.bluetoothScales = [
            makeDevice(id: "paired-1", broadcastIdString: "AA11"),
            makeDevice(id: "paired-2", broadcastIdString: "BB22")
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
        connectedOne.isWeighOnlyModeEnabledByOthers = true
        let connectedTwo = makeDevice(id: "connected-2", broadcastIdString: "BB22", isConnected: true)
        connectedTwo.isWeighOnlyModeEnabledByOthers = true
        let disconnected = makeDevice(id: "disconnected-1", broadcastIdString: "CC33", isConnected: false)
        sut.bluetoothScales = [connectedOne, connectedTwo, disconnected]
        sut.skipDevices = ["STALE-ID"]

        await sut.disconnectConnectedScales()

        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 2)
        #expect(connectedOne.isWeighOnlyModeEnabledByOthers == false)
        #expect(connectedTwo.isWeighOnlyModeEnabledByOthers == false)
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

    @Test("deleteCurrentUserFromScaleIfPossible with missing broadcast id fails before SDK work")
    func deleteCurrentUserFromScaleIfPossibleMissingBroadcastId() async {
        let logger = MockLoggerService()
        let sut = makeSUT(logger: logger)

        let result = await sut.deleteCurrentUserFromScaleIfPossible(makeDevice(broadcastIdString: nil), disconnect: false)

        switch result {
        case .success:
            Issue.record("Expected deleteCurrentUserFromScaleIfPossible to fail")
        case .failure(let error):
            guard case .invalidBroadcastId = error else {
                Issue.record("Expected invalidBroadcastId, got \(error)")
                return
            }
        }

        #expect(logger.messages.contains { $0.contains("missing broadcastId") })
    }

    @Test("deleteCurrentUserFromScaleIfPossible returns deviceNotConnected when user lookup cannot start")
    func deleteCurrentUserFromScaleIfPossibleDisconnectedDevice() async {
        let sut = makeSUT()
        let disconnectedDevice = makeDevice(id: "offline-1", broadcastIdString: "AA11", isConnected: false)

        let result = await sut.deleteCurrentUserFromScaleIfPossible(disconnectedDevice, disconnect: true)

        switch result {
        case .success:
            Issue.record("Expected deleteCurrentUserFromScaleIfPossible to fail")
        case .failure(let error):
            guard case .deviceNotConnected = error else {
                Issue.record("Expected deviceNotConnected, got \(error)")
                return
            }
        }

        #expect(sut.blockedBroadcastIds.isEmpty)
        #expect(sut.skipDevices.isEmpty)
    }

    @Test("core operations reject invalid broadcast ids consistently")
    func invalidBroadcastOperationsFailConsistently() async {
        let sut = makeSUT()
        let invalidDevice = makeDevice(id: "invalid-1", broadcastIdString: nil, isConnected: true)

        expectInvalidBroadcast(await sut.confirmSmartPair(device: invalidDevice, token: "token", displayName: "User", userNumber: 1))
        expectInvalidBroadcast(await sut.deleteDevice(invalidDevice, disconnect: true))
        expectInvalidBroadcast(await sut.getWifiList(for: invalidDevice))
        expectInvalidBroadcast(await sut.setupWifi(on: invalidDevice, config: WifiConfig(ssid: "Test", password: "pass")))
        expectInvalidBroadcast(await sut.cancelWifi(on: invalidDevice))
        expectInvalidBroadcast(await sut.getWifiMacAddress(for: invalidDevice))
        expectInvalidBroadcast(await sut.startLiveMeasurement(for: invalidDevice))
        expectInvalidBroadcast(await sut.stopLiveMeasurement(for: invalidDevice))
        expectInvalidBroadcast(await sut.updateSetting(on: invalidDevice, settings: [DeviceSetting(key: "SESSION_IMPEDANCE", value: .bool(true))]))
        expectInvalidBroadcast(await sut.clearData(on: invalidDevice, dataType: .all))
        expectInvalidBroadcast(await sut.updateAccount(on: invalidDevice, preference: makePreference(id: "pref-1")))
        expectInvalidBroadcast(await sut.getScaleUserList(for: invalidDevice, skipConnectionCheck: true))
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
        discovery: MockBLEDiscoveryManager? = nil
    ) -> BluetoothService {
        BluetoothService(
            accountService: account ?? MockAccountService(),
            scaleService: scale ?? MockScaleService(),
            entryService: entry ?? MockEntryService(),
            logger: logger ?? MockLoggerService(),
            discoveryManager: discovery ?? MockBLEDiscoveryManager()
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

private enum CoreOpsTestError: Error {
    case unexpected
}
