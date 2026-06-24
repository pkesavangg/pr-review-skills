import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
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

    @Test("startBluetoothOperations when already started: does not restart discovery")
    func startBluetoothOperationsAlreadyStarted() async {
        let discovery = MockBLEDiscoveryManager()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, discovery: discovery)
        sut.isSmartScanStarted = true

        await sut.startBluetoothOperations()

        #expect(discovery.clearDevicesCalls == 0)
        #expect(sut.isSmartScanStarted == true)
    }

    @Test("initialize account subscription updates active account on service")
    func initializeAccountSubscriptionUpdatesActiveAccount() async {
        let account = MockAccountService()
        let sut = makeSUT(account: account)
        let expectedAccount = AccountTestFixtures.makeAccountSnapshot(id: "222", email: "user2@example.com", isLoggedIn: true, isActiveAccount: true)

        account.activeAccount = expectedAccount
        let updated = await waitUntil { sut.activeAccount?.accountId == "222" }

        #expect(updated == true)
    }

    @Test("scale subscription filters to bluetooth scale source types")
    func scalesSubscriptionFiltersBluetoothTypes() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)

        let bluetoothScale = makeDevice(
            id: "keep-1",
            broadcastIdString: "AA11",
            bathScale: BathScale(scaleType: ScaleSourceType.bluetoothScale.rawValue, bodyComp: true)
        )
        let wifiScale = makeDevice(
            id: "drop-1",
            broadcastIdString: "BB22",
            bathScale: BathScale(scaleType: ScaleSourceType.wifi.rawValue, bodyComp: true)
        )

        scale.scales = [bluetoothScale.toSnapshot(), wifiScale.toSnapshot()]
        let updated = await waitUntil { sut.bluetoothScales.count == 1 }

        #expect(updated == true)
        #expect(sut.bluetoothScales.first?.id == "keep-1")
    }

    @Test("scale subscription with empty list clears bluetooth scales")
    func scalesSubscriptionEmptyListClearsBluetoothScales() async {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)

        sut.bluetoothScales = [makeDevice(id: "existing-1", broadcastIdString: "C1").toSnapshot()]
        scale.scales = []
        let updated = await waitUntil { sut.bluetoothScales.isEmpty }

        #expect(updated == true)
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

    @Test("publisher getters emit from underlying subjects")
    func publisherGettersEmitFromSubjects() async {
        let sut = makeSUT()
        var cancellables = Set<AnyCancellable>()

        let discoveryEvent = DeviceDiscoveryEvent(
            device: makeDevice(id: "publisher-device-1", broadcastIdString: "P1").toSnapshot(),
            deviceInfo: ScaleItemInfo(productName: "Test Scale", sku: "SKU-1", imgPath: "image", setupType: .bluetooth, bodyComp: true),
            protocolType: .A6,
            isNew: true
        )
        let info = DeviceInfo(deviceName: "Scale", broadcastIdString: "P1", macAddress: "AA:BB")
        let entry = EntryNotification(from: BathScaleOperationDTO(
            accountId: "101",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "2026-02-01T00:00:00.000Z",
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "CREATE",
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: EntrySource.bluetooth.rawValue,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: "kg",
            visceralFatLevel: nil,
            water: nil,
            weight: 72.5
        ))
        let firmware = FirmwareUpdateStatus(progress: 0.42, isComplete: false, error: nil)

        var discovered: [DeviceDiscoveryEvent] = []
        var infos: [DeviceInfo] = []
        var weightOnlyAlerts: [Bool] = []
        var entries: [EntryNotification] = []
        var firmwareStates: [FirmwareUpdateStatus] = []

        sut.deviceDiscoveredPublisher
            .sink { discovered.append($0) }
            .store(in: &cancellables)
        sut.deviceInfoUpdatedPublisher
            .sink { infos.append($0) }
            .store(in: &cancellables)
        sut.showWeightOnlyModeAlertPublisher
            .sink { weightOnlyAlerts.append($0) }
            .store(in: &cancellables)
        sut.newEntryReceivedPublisher
            .sink { entries.append($0) }
            .store(in: &cancellables)
        sut.firmwareUpdateProgressPublisher
            .sink { firmwareStates.append($0) }
            .store(in: &cancellables)
        _ = sut.liveMeasurementPublisher

        #expect(cancellables.count == 5)
        await Task.yield()

        sut.deviceDiscoveredSubject.send(discoveryEvent)
        sut.deviceInfoUpdatedSubject.send(info)
        sut.showWeightOnlyModeAlertSubject.send(true)
        sut.newEntryReceivedSubject.send(entry)
        sut.firmwareUpdateProgressSubject.send(firmware)
        try? await Task.sleep(nanoseconds: 200_000_000)

        #expect(discovered.count == 1)
        #expect(discovered.first == discoveryEvent)
        #expect(infos.count == 1)
        #expect(infos.first == info)
        #expect(weightOnlyAlerts == [true])
        #expect(entries.count == 1)
        #expect(entries.first == entry)
        #expect(firmwareStates == [firmware])
        #expect(cancellables.count == 5)
    }

    @Test("clearScaleDiscoveredInfo clears both skip and reconnect skipped lists")
    func clearScaleDiscoveredInfoClearsLists() {
        let sut = makeSUT()
        sut.skipDevices = ["S1", "S2"]
        sut.reconnectAlertSkippedDevices = ["R1"]

        sut.clearScaleDiscoveredInfo()

        #expect(sut.skipDevices.isEmpty)
        #expect(sut.reconnectAlertSkippedDevices.isEmpty)
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
        sut.bluetoothScales = [makeDevice(id: "d1", broadcastIdString: "PAIRED_1").toSnapshot()]

        sut.reapplySkipDevicesExcludingPaired()

        #expect(sut.skipDevices == ["KEEP_ME"])
    }

    @Test("getDeviceInfo when device not connected (Bluetooth off / connection lost): returns deviceNotConnected")
    func getDeviceInfoNotConnected() async {
        let sut = makeSUT()
        let device = makeDevice(isConnected: false)

        let result = await sut.getDeviceInfo(broadcastId: device.broadcastIdString ?? "")

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
        discovery: MockBLEDiscoveryManager? = nil,
        notification: MockNotificationHelperService? = nil
    ) -> BluetoothService {
        BluetoothService(
            accountService: account ?? MockAccountService(),
            scaleService: scale ?? MockScaleService(),
            entryService: entry ?? MockEntryService(),
            babyService: MockBabyService(),
            logger: logger ?? MockLoggerService(),
            discoveryManager: discovery ?? MockBLEDiscoveryManager(),
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

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }
}
