import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BluetoothServiceScanEventPipelineTests {

    @Test("startSmartScan publishes a new discovery event for a valid NEW_DEVICE response")
    func startSmartScanPublishesDiscoveryEventForNewDevice() async throws {
        let sdk = MockBluetoothSDKClient()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-scan-1", email: "scan1@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-scan-1" }
        let deviceDetails = makeDeviceDetails(broadcastId: "AA11", protocolType: "A6")
        let expectedBroadcastId = expectedMappedBroadcastId(from: "AA11")

        try await sut.startSmartScan()
        let scanStarted = await waitUntil { sut.isSmartScanStarted }
        let discovered = await collectValues(count: 1, from: sut.deviceDiscoveredPublisher) {
            await sendScanResponse(makeScanResponse(type: .NEW_DEVICE, data: deviceDetails), through: sdk)
        }

        #expect(scanStarted == true)
        #expect(sdk.scannedAppTypes.count == 1)
        #expect(discovered.first?.device.broadcastIdString == expectedBroadcastId)
        #expect(discovered.first?.protocolType == .A6)
        #expect(discovered.first?.isNew == true)
    }

    @Test("blocked or malformed scan events are filtered before downstream processing")
    func blockedOrMalformedScanEventsAreFiltered() async throws {
        let sdk = MockBluetoothSDKClient()
        let logger = MockLoggerService()
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-scan-3", email: "scan3@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, logger: logger, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-scan-3" }
        sut.blockedBroadcastIds.insert("BLOCKED-1")

        var discovered: [DeviceDiscoveryEvent] = []
        var cancellables = Set<AnyCancellable>()
        sut.deviceDiscoveredPublisher
            .sink { discovered.append($0) }
            .store(in: &cancellables)
        await Task.yield()

        try await sut.startSmartScan()
        await Task.yield()
        await sendScanResponse(makeScanResponse(type: .NEW_DEVICE, data: makeDeviceDetails(broadcastId: "BLOCKED-1")), through: sdk)
        await sendScanResponse(makeScanResponse(type: nil, data: makeDeviceDetails(broadcastId: "UNUSED-1")), through: sdk)
        await sendScanResponse(makeScanResponse(type: .LIVE_MEASUREMENT, data: makeDeviceDetails(broadcastId: "BAD-LIVE-1")), through: sdk)
        let blockedSkipped = await waitUntil { sdk.skippedDevices.count == 1 }
        let loggedMalformed = await waitUntil { logger.messages.contains { $0.contains("Failed to get live measurement data") } }

        #expect(discovered.isEmpty)
        #expect(blockedSkipped == true)
        #expect(sdk.skippedDevices.first?.broadcastId == "BLOCKED-1")
        #expect(loggedMalformed == true)
    }

    @Test("single and multi-entry responses fire pending notifications and ignore malformed batches safely")
    func entryResponsesSaveValidEntriesAndIgnoreMalformedBatches() async throws {
        let sdk = MockBluetoothSDKClient()
        let account = MockAccountService()
        let entry = MockEntryService()
        let logger = MockLoggerService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-entry", email: "entry@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, entry: entry, logger: logger, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-entry" }

        try await sut.startSmartScan()
        // Scale entries now fire pendingScaleEntryPublisher (not newEntryReceivedPublisher)
        // because the entry is held pending user confirmation before saving.
        let notifications = await collectValues(count: 2, from: sut.pendingScaleEntryPublisher) {
            await sendScanResponse(
                makeScanResponse(type: .SINGLE_ENTRY, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_000_000, weightInKg: 72.5)),
                through: sdk
            )
            await sendScanResponse(
                makeScanResponse(type: .SINGLE_ENTRY, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_100_000, weightInKg: 73.0)),
                through: sdk
            )
            await sendScanResponse(makeScanResponse(type: .MULTI_ENTRIES, data: MalformedScanData()), through: sdk)
        }

        #expect(notifications.count == 2)
        #expect(notifications.first?.accountId == "acct-entry")
        // Entries are not saved until confirmed — entryService must not have been called yet
        #expect(entry.savedEntries.isEmpty)
        #expect(logger.messages.contains { $0.contains("Bluetooth service initialize called") })
        #expect(logger.messages.contains { $0.contains("No valid entries") } == false)
    }

    @Test("confirming after a displaced entry saves both the displaced and the primary entry")
    func confirmSavesBothDisplacedAndPrimaryEntry() async throws {
        let sdk = MockBluetoothSDKClient()
        let account = MockAccountService()
        let entry = MockEntryService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-confirm", email: "confirm@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, entry: entry, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-confirm" }

        try await sut.startSmartScan()
        // Two readings arrive before the user acts: the first becomes displaced, the second is pending.
        _ = await collectValues(count: 2, from: sut.pendingScaleEntryPublisher) {
            await sendScanResponse(
                makeScanResponse(type: .SINGLE_ENTRY, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_000_000, weightInKg: 70.0)),
                through: sdk
            )
            await sendScanResponse(
                makeScanResponse(type: .SINGLE_ENTRY, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_100_000, weightInKg: 71.0)),
                through: sdk
            )
        }

        #expect(entry.savedEntries.isEmpty)
        #expect(sut.displacedPendingEntries.count == 1)

        try await sut.confirmPendingScaleEntry()

        #expect(entry.savedEntries.count == 2)
        #expect(sut.displacedPendingEntries.isEmpty)
        #expect(sut.pendingScaleEntry == nil)
    }

    @Test("discarding after a displaced entry drops both the displaced and the primary entry")
    func discardDropsBothDisplacedAndPrimaryEntry() async throws {
        let sdk = MockBluetoothSDKClient()
        let account = MockAccountService()
        let entry = MockEntryService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-discard", email: "discard@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, entry: entry, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-discard" }

        try await sut.startSmartScan()
        _ = await collectValues(count: 2, from: sut.pendingScaleEntryPublisher) {
            await sendScanResponse(
                makeScanResponse(type: .SINGLE_ENTRY, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_000_000, weightInKg: 70.0)),
                through: sdk
            )
            await sendScanResponse(
                makeScanResponse(type: .SINGLE_ENTRY, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_100_000, weightInKg: 71.0)),
                through: sdk
            )
        }

        #expect(sut.displacedPendingEntries.count == 1)

        sut.discardPendingScaleEntry()

        #expect(entry.savedEntries.isEmpty)
        #expect(sut.displacedPendingEntries.isEmpty)
        #expect(sut.pendingScaleEntry == nil)
    }

//    @Test("device connected updates connection state, weight-only status, and debounced alert visibility")
//    func deviceConnectedUpdatesStateAndShowsDebouncedAlert() async throws {
//        let rawBroadcastId = "AA11"
//        let storedBroadcastId = expectedMappedBroadcastId(from: rawBroadcastId)
//        let sdk = MockBluetoothSDKClient()
//        sdk.getDeviceInfoResult = makeDeviceDetails(broadcastId: rawBroadcastId, protocolType: "R4", impedanceSwitchState: false)
//        let account = MockAccountService()
//        let scale = MockScaleService()
//        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-r4", email: "r4@example.com", isLoggedIn: true, isActiveAccount: true)
//        let sut = makeSUT(account: account, scale: scale, sdk: sdk)
//        _ = await waitUntil { sut.activeAccount?.accountId == "acct-r4" }
//        let device = makeDevice(
//            id: "r4-scale-1",
//            broadcastIdString: storedBroadcastId,
//            isConnected: true,
//            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
//        )
//        device.r4ScalePreference = makePreference(id: "r4-scale-1", shouldMeasureImpedance: true)
//        sut.bluetoothScales = [device]
//
//        try await sut.startSmartScan()
//        let alerts = await collectValues(count: 1, from: sut.showWeightOnlyModeAlertPublisher, timeoutNanoseconds: 3_000_000_000) {
//            await sendScanResponse(
//                makeScanResponse(type: .DEVICE_CONNECTED, data: makeDeviceDetails(broadcastId: rawBroadcastId, protocolType: "R4")),
//                through: sdk
//            )
//        }
//        let updatedWeightOnly = await waitUntil(timeoutNanoseconds: 2_000_000_000) { scale.updateConnectedDeviceWeightOnlyModeCalls == 1 }
//
//        #expect(scale.updateConnectedDevicesCalls == 1)
//        #expect(updatedWeightOnly == true)
//        #expect(device.isWeighOnlyModeEnabledByOthers == true)
//        #expect(alerts.last == true)
//    }

//    @Test("device disconnected clears weight-only status and keeps pipeline consistent")
//    func deviceDisconnectedClearsWeightOnlyState() async throws {
//        let sdk = MockBluetoothSDKClient()
//        let account = MockAccountService()
//        let scale = MockScaleService()
//        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-r4-disconnect", email: "r4d@example.com", isLoggedIn: true, isActiveAccount: true)
//        let sut = makeSUT(account: account, scale: scale, sdk: sdk)
//        _ = await waitUntil { sut.activeAccount?.accountId == "acct-r4-disconnect" }
//        let device = makeDevice(id: "r4-scale-2", broadcastIdString: "R4-2", isConnected: true, bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true))
//        device.isWeighOnlyModeEnabledByOthers = true
//        sut.bluetoothScales = [device]
//
//        try await sut.startSmartScan()
//        let alerts = await collectValues(count: 1, from: sut.showWeightOnlyModeAlertPublisher, timeoutNanoseconds: 3_000_000_000) {
//            await sendScanResponse(makeScanResponse(type: .DEVICE_DISCONNECTED, data: makeDeviceDetails(broadcastId: "R4-2", protocolType: "R4")), through: sdk)
//        }
//
//        #expect(scale.updateConnectedDevicesCalls == 1)
//        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 1)
//        #expect(device.isWeighOnlyModeEnabledByOthers == false)
//        #expect(alerts.last == false)
//    }

    @Test("wifi and device info updates route to the expected service and publishers")
    func wifiAndDeviceInfoUpdatesRouteCorrectly() async throws {
        let sdk = MockBluetoothSDKClient()
        sdk.getDeviceInfoResult = makeDeviceDetails(broadcastId: "INFO-1", protocolType: "R4", impedanceSwitchState: true)
        let account = MockAccountService()
        let scale = MockScaleService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-info", email: "info@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, scale: scale, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-info" }
        let device = makeDevice(
            id: "info-scale-1",
            broadcastIdString: "INFO-1",
            isConnected: true,
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        device.r4ScalePreference = makePreference(id: "info-scale-1", shouldMeasureImpedance: true)
        sut.bluetoothScales = [device.toSnapshot()]

        try await sut.startSmartScan()
        let infos = await collectValues(count: 1, from: sut.deviceInfoUpdatedPublisher, timeoutNanoseconds: 3_000_000_000) {
            await sendScanResponse(
                makeScanResponse(
                    type: .WIFI_STATUS_UPDATE,
                    data: makeDeviceDetails(broadcastId: "INFO-1", protocolType: "R4", isWifiConfigured: true)
                ),
                through: sdk
            )
            await sendScanResponse(
                makeScanResponse(
                    type: .DEVICE_INFO_UPDATE,
                    data: makeDeviceDetails(broadcastId: "INFO-1", protocolType: "R4", impedanceSwitchState: true)
                ),
                through: sdk
            )
        }
        let routed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            scale.updateConnectedDeviceWifiStatusCalls == 1 &&
            scale.updateConnectedDevicesCalls == 2
        }

        #expect(routed == true)
        #expect(infos.first?.broadcastIdString == "INFO-1")
    }

    @Test("device info update with malformed payload logs and avoids publisher side effects")
    func deviceInfoUpdateWithMalformedPayloadLogsAndSkipsPublishing() async throws {
        let sdk = MockBluetoothSDKClient()
        let account = MockAccountService()
        let logger = MockLoggerService()
        let scale = MockScaleService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-malformed", email: "bad@example.com", isLoggedIn: true, isActiveAccount: true)
        let sut = makeSUT(account: account, scale: scale, logger: logger, sdk: sdk)
        _ = await waitUntil { sut.activeAccount?.accountId == "acct-malformed" }

        var infos: [DeviceInfo] = []
        var cancellables = Set<AnyCancellable>()
        sut.deviceInfoUpdatedPublisher
            .sink { infos.append($0) }
            .store(in: &cancellables)
        await Task.yield()

        try await sut.startSmartScan()
        await Task.yield()
        await sendScanResponse(
            makeScanResponse(type: .DEVICE_INFO_UPDATE, data: makeEntry(protocolType: "A6", timestamp: 1_730_000_200_000, weightInKg: 70.0)),
            through: sdk
        )
        let loggedMalformed = await waitUntil {
            logger.messages.contains { $0.contains("DEVICE_INFO_UPDATE: Failed to cast data to GGDeviceDetails") }
        }

        #expect(scale.updateConnectedDevicesCalls == 1)
        #expect(infos.isEmpty)
        #expect(loggedMalformed == true)
    }

    @Test("weight-only alert debounce emits only the final stable state")
    func weightOnlyAlertDebounceEmitsFinalStateOnly() async {
        let sut = makeSUT()
        let deviceBase = makeDevice(
            id: "alert-scale-1",
            broadcastIdString: "ALERT-1",
            isConnected: true,
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        deviceBase.isWeighOnlyModeEnabledByOthers = true
        let snapshotOn = deviceBase.toSnapshot()
        deviceBase.isWeighOnlyModeEnabledByOthers = false
        let snapshotOff = deviceBase.toSnapshot()

        let alerts = await collectValues(count: 1, from: sut.showWeightOnlyModeAlertPublisher, timeoutNanoseconds: 3_000_000_000) {
            sut.bluetoothScales = [snapshotOn]
            let first = Task { @MainActor in
                await sut.checkCanShowWeightOnlyModeAlert()
            }

            try? await Task.sleep(nanoseconds: 100_000_000)
            sut.bluetoothScales = [snapshotOff]
            let second = Task { @MainActor in
                await sut.checkCanShowWeightOnlyModeAlert()
            }

            await first.value
            await second.value
        }
        #expect(alerts == [false])
    }

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

    private func makePreference(id: String, shouldMeasureImpedance: Bool) -> R4ScalePreference {
        R4ScalePreference(
            scaleId: id,
            displayName: "Test User",
            displayMetrics: ["weight"],
            shouldFactoryReset: false,
            shouldMeasureImpedance: shouldMeasureImpedance,
            shouldMeasurePulse: true,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
    }

    private func sendScanResponse(_ response: GGScanResponse, through sdk: MockBluetoothSDKClient) async {
        sdk.scanCallbacks.last?(.success(response))
        await Task.yield()
    }

    private func expectedMappedBroadcastId(from rawBroadcastId: String) -> String {
        rawBroadcastId.uppercased().padding(toLength: 8, withPad: "0", startingAt: 0)
    }

    private func collectValues<T: Sendable>(
        count: Int,
        from publisher: AnyPublisher<T, Never>,
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        action: @escaping @MainActor () async -> Void
    ) async -> [T] {
        var values: [T] = []
        var cancellable: AnyCancellable?

        cancellable = publisher
            .sink { values.append($0) }
        await Task.yield()

        await action()
        _ = await waitUntil(timeoutNanoseconds: timeoutNanoseconds) { values.count == count }
        cancellable?.cancel()
        return values
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

private func makeScanResponse(type: ScanResponseType?, data: GGScanResponseData) -> GGScanResponse {
    GGScanResponse(type: type, data: data)
}

private func makeDeviceDetails(
    broadcastId: String,
    protocolType: String = "A6",
    isWifiConfigured: Bool = true,
    impedanceSwitchState: Bool = true
) -> GGDeviceDetails {
    decodeJSON(
        [
            "manufacturerName": "Weight Gurus",
            "modelNumber": "Model-\(protocolType)",
            "serialNumber": "Serial-\(broadcastId)",
            "firmwareRevision": "FW-1",
            "hardwareRevision": "HW-1",
            "softwareRevision": "SW-1",
            "systemID": "SYS-\(broadcastId)",
            "deviceName": protocolType == "R4" ? "Smart Scale" : "Scale",
            "broadcastId": broadcastId,
            "broadcastIdString": broadcastId,
            "password": "00000000",
            "macAddress": "AA:BB:CC:DD:EE:FF",
            "wifiMacAddress": "11:22:33:44:55:66",
            "identifier": "identifier-\(broadcastId)",
            "protocolType": protocolType,
            "isWifiConfigured": isWifiConfigured,
            "sessionImpedanceSwitchState": impedanceSwitchState,
            "impedanceSwitchState": impedanceSwitchState,
            "startAnimationState": true,
            "endAnimationState": false,
            "batteryLevel": 90,
            "userNumber": 2,
            "heartRateState": true
        ],
        as: GGDeviceDetails.self
    )
}

private func makeEntry(protocolType: String, timestamp: Int, weightInKg: Float) -> GGEntry {
    decodeJSON(
        [
            "date": timestamp,
            "protocolType": protocolType,
            "weightInKg": weightInKg,
            "weight": weightInKg * 2.20462,
            "bodyFat": 24.2,
            "muscleMass": 31.1,
            "water": 51.0,
            "bmi": 0,
            "bmr": 150,
            "metabolicAge": 30,
            "proteinPercent": 16.4,
            "pulse": 70,
            "skeletalMusclePercent": 41.2,
            "subcutaneousFatPercent": 17.5,
            "visceralFatLevel": 9,
            "boneMass": 3.1,
            "impedance": 510.0,
            "unit": "kg"
        ],
        as: GGEntry.self
    )
}

private func encodeJSONObject<T: Encodable>(_ value: T) -> Any {
    let data = try! JSONEncoder().encode(value) // swiftlint:disable:this force_try
    return try! JSONSerialization.jsonObject(with: data) // swiftlint:disable:this force_try
}

private func decodeJSON<T: Decodable>(_ object: [String: Any], as type: T.Type) -> T {
    let data = try! JSONSerialization.data(withJSONObject: object) // swiftlint:disable:this force_try
    return try! JSONDecoder().decode(type, from: data) // swiftlint:disable:this force_try
}

private struct MalformedScanData: GGScanResponseData {}
