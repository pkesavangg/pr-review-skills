import Foundation
import Testing
@testable import meApp

@MainActor
struct BluetoothServiceTests {

    @Test("discovery controls: stop, clear, pause, resume, scanForPairing delegate to discovery manager")
    func discoveryControlsDelegateToManager() async {
        let discovery = MockBLEDiscoveryManager()
        let sut = makeSUT(discovery: discovery)

        sut.isSmartScanStarted = true
        sut.skipDevices = ["A1", "B2"]

        sut.stopScan()
        sut.clearDevices()
        sut.pauseSmartScan()
        sut.resumeSmartScan(clearOnlyPairing: true)
        sut.scanForPairing()

        #expect(sut.isSmartScanStarted == false)
        #expect(sut.skipDevices.isEmpty)
        #expect(discovery.stopScanCalls == 1)
        #expect(discovery.clearDevicesCalls == 1)
        #expect(discovery.pauseScanCalls == 1)
        #expect(discovery.resumeScanCalls == 1)
        #expect(discovery.lastResumeClearOnlyPairing == true)
        #expect(discovery.scanForPairingCalls == 1)
    }

    @Test("startBluetoothOperations with no active account: returns early")
    func startBluetoothOperationsNoActiveAccount() async {
        let discovery = MockBLEDiscoveryManager()
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account, discovery: discovery)

        await sut.startBluetoothOperations()

        #expect(discovery.clearDevicesCalls == 0)
    }

    @Test("account cleared while scan started: service stops scan")
    func accountClearedStopsScan() async {
        let discovery = MockBLEDiscoveryManager()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let sut = makeSUT(account: account, discovery: discovery)

        sut.isSmartScanStarted = true
        account.activeAccount = nil
        try? await Task.sleep(nanoseconds: 150_000_000)

        #expect(sut.isSmartScanStarted == false)
        #expect(discovery.stopScanCalls >= 1)
    }

    @Test("confirmSmartPair invalid broadcast id: returns invalidBroadcastId")
    func confirmSmartPairInvalidBroadcast() async {
        let sut = makeSUT()
        let device = makeDevice(broadcastIdString: nil)

        let result = await sut.confirmSmartPair(device: device, token: "t", displayName: "u", userNumber: 1)

        switch result {
        case .success:
            Issue.record("Expected failure")
        case .failure(let error):
            guard case .invalidBroadcastId = error else {
                Issue.record("Expected invalidBroadcastId, got \(error)")
                return
            }
        }
    }

    @Test("disconnectDevice success: adds to skip list and blocks broadcast id")
    func disconnectDeviceSuccess() async {
        let sut = makeSUT()

        let result = await sut.disconnectDevice(broadcastId: "ABC123", considerForSession: true)

        switch result {
        case .success:
            #expect(sut.skipDevices.contains("ABC123"))
            #expect(sut.blockedBroadcastIds.contains("ABC123"))
            #expect(sut.canShowScaleDiscoveredModal == false)
        case .failure(let error):
            Issue.record("Expected success, got \(error)")
        }
    }

    @Test("reapplySkipDevicesExcludingPaired removes paired ids from skip list")
    func reapplySkipDevicesExcludingPaired() {
        let sut = makeSUT()
        sut.skipDevices = ["KEEP_ME", "PAIRED_1"]
        sut.bluetoothScales = [makeDevice(id: "d1", broadcastIdString: "PAIRED_1")]

        sut.reapplySkipDevicesExcludingPaired()

        #expect(sut.skipDevices == ["KEEP_ME"])
    }

    @Test("getDeviceInfo when device not connected (Bluetooth off / connection lost): returns deviceNotConnected")
    func getDeviceInfoNotConnected() async {
        let sut = makeSUT()
        let device = makeDevice(isConnected: false)

        let result = await sut.getDeviceInfo(for: device)

        switch result {
        case .success:
            Issue.record("Expected failure")
        case .failure(let error):
            guard case .deviceNotConnected = error else {
                Issue.record("Expected deviceNotConnected, got \(error)")
                return
            }
        }
    }

    @Test("startSmartScan with no active account: throws noActiveAccount")
    func startSmartScanNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            try await sut.startSmartScan()
            Issue.record("Expected noActiveAccount")
        } catch {
            guard case .noActiveAccount = error as? BluetoothServiceError else {
                Issue.record("Expected noActiveAccount, got \(error)")
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
        isConnected: Bool? = true
    ) -> Device {
        Device(
            id: id,
            accountId: "101",
            deviceName: "Scale",
            broadcastIdString: broadcastIdString,
            protocolType: "A6",
            isConnected: isConnected
        )
    }
}
