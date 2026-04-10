import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BluetoothServiceBpmTests {

    // MARK: - BPM Discovery Event Tests

    @Test("BPM discovery event: deviceCategory is .bpm when setupType is .bpm")
    func bpmDiscoveryEventHasBpmCategory() {
        let device = BluetoothTestFixtures.makeDevice(id: "bpm-1", broadcastIdString: "BPM001")
        let bpmInfo = ScaleItemInfo(
            productName: "Blood Pressure Monitor",
            sku: "0603",
            imgPath: "0603",
            setupType: .bpm,
            bodyComp: false
        )

        let event = DeviceDiscoveryEvent(
            device: device,
            deviceInfo: bpmInfo,
            protocolType: .A6,
            isNew: true,
            deviceCategory: .bpm
        )

        #expect(event.deviceCategory == .bpm)
        #expect(event.isNew == true)
        #expect(event.deviceInfo.sku == "0603")
        #expect(event.deviceInfo.setupType == .bpm)
    }

    @Test("Scale discovery event: deviceCategory defaults to .scale")
    func scaleDiscoveryEventDefaultsToScale() {
        let device = BluetoothTestFixtures.makeDevice(id: "scale-1", broadcastIdString: "SC001")
        let scaleInfo = ScaleItemInfo(
            productName: "Bluetooth Smart Scale",
            sku: "0375",
            imgPath: "0375",
            setupType: .bluetooth,
            bodyComp: false
        )

        let event = DeviceDiscoveryEvent(
            device: device,
            deviceInfo: scaleInfo,
            protocolType: .A6,
            isNew: true
        )

        #expect(event.deviceCategory == .scale)
    }

    // MARK: - BPM Measurement Model Tests

    @Test("BpmMeasurement: init sets all properties")
    func bpmMeasurementInit() {
        let date = Date()
        let measurement = BpmMeasurement(
            systolic: 120,
            diastolic: 80,
            pulse: 72,
            meanArterial: "93.3",
            irregularHb: true,
            timestamp: date,
            broadcastId: "BPM001"
        )

        #expect(measurement.systolic == 120)
        #expect(measurement.diastolic == 80)
        #expect(measurement.pulse == 72)
        #expect(measurement.meanArterial == "93.3")
        #expect(measurement.irregularHb == true)
        #expect(measurement.timestamp == date)
        #expect(measurement.broadcastId == "BPM001")
    }

    @Test("BpmMeasurement: defaults for optional fields")
    func bpmMeasurementDefaults() {
        let measurement = BpmMeasurement(systolic: 130, diastolic: 85, pulse: 68)

        #expect(measurement.meanArterial == nil)
        #expect(measurement.irregularHb == false)
        #expect(measurement.broadcastId == nil)
    }

    // MARK: - BPM Publisher Tests

    @Test("newBpmReadingReceivedPublisher emits from subject")
    func bpmPublisherEmitsFromSubject() async {
        let sut = makeSUT()
        var cancellables = Set<AnyCancellable>()
        var received: [BpmMeasurement] = []

        sut.newBpmReadingReceivedPublisher
            .sink { received.append($0) }
            .store(in: &cancellables)

        let measurement = BpmMeasurement(systolic: 120, diastolic: 80, pulse: 72, broadcastId: "BPM001")
        sut.newBpmReadingReceivedSubject.send(measurement)
        try? await Task.sleep(nanoseconds: 100_000_000)

        #expect(received.count == 1)
        #expect(received.first?.systolic == 120)
        #expect(received.first?.diastolic == 80)
        #expect(received.first?.pulse == 72)
    }

    // MARK: - BPM SKU Constants Tests

    @Test("BPM SKU constants contain expected values")
    func bpmSkuConstants() {
        #expect(bpmSkus.contains("0603"))
        #expect(bpmSkus.contains("0661"))
        #expect(bpmSkus.contains("0634"))
        #expect(bpmSkus.contains("0663"))
        #expect(bpmSkus.contains("0604"))
        #expect(bpmSkus.contains("0636"))
        #expect(bpmSkus.contains("0664"))
        #expect(bpmSkus.contains("0665"))
        #expect(bpmSkus.contains("0667"))
        #expect(bpmSkus.contains("0639"))
        #expect(bpmSkus.count == 10)
    }

    @Test("BPMS array has correct entries")
    func bpmsArrayEntries() {
        #expect(BPMS.count == 6)
        #expect(BPMS.allSatisfy { $0.setupType == .bpm })
        #expect(BPMS.allSatisfy { $0.bodyComp == false })
        #expect(BPMS.map(\.sku).sorted() == ["0603", "0604", "0634", "0636", "0661", "0663"])
    }

    // MARK: - ScaleInfoUtils BPM Lookup Tests

    @Test("ScaleInfoUtils includes BPM devices in scales list")
    func scaleInfoUtilsIncludesBpm() {
        let utils = ScaleInfoUtils()
        let bpmInfo = utils.getScaleInfo(bySku: "0603")

        #expect(bpmInfo != nil)
        #expect(bpmInfo?.setupType == .bpm)
        #expect(bpmInfo?.productName == "Smart Wrist Blood Pressure Monitor")
    }

    @Test("ScaleInfoUtils isBpmDevice returns true for BPM SKUs")
    func scaleInfoUtilsIsBpmDevice() {
        let utils = ScaleInfoUtils()

        #expect(utils.isBpmDevice(sku: "0603") == true)
        #expect(utils.isBpmDevice(sku: "0661") == true)
        #expect(utils.isBpmDevice(sku: "0375") == false)
        #expect(utils.isBpmDevice(sku: "0412") == false)
    }

    // MARK: - Mock BPM Operations Tests

    @Test("MockBluetoothService: scanForBpm increments call count")
    func mockScanForBpm() {
        let mock = MockBluetoothService()
        mock.scanForBpm()
        mock.scanForBpm()

        #expect(mock.scanForBpmCalls == 2)
    }

    @Test("MockBluetoothService: connectBpm records broadcast ID")
    func mockConnectBpm() async {
        let mock = MockBluetoothService()
        _ = await mock.connectBpm(broadcastId: "BPM001", userNumber: 1)

        #expect(mock.connectBpmCalls == 1)
        #expect(mock.lastConnectBpmBroadcastId == "BPM001")
    }

    @Test("MockBluetoothService: receiveBpmReading records broadcast ID")
    func mockReceiveBpmReading() async {
        let mock = MockBluetoothService()
        _ = await mock.receiveBpmReading(broadcastId: "BPM002")

        #expect(mock.receiveBpmReadingCalls == 1)
        #expect(mock.lastReceiveBpmBroadcastId == "BPM002")
    }

    // MARK: - BPM Service Operations Tests

    @Test("scanForBpm delegates to discovery manager")
    func scanForBpmDelegatesToManager() {
        let discovery = MockBLEDiscoveryManager()
        let sut = makeSUT(discovery: discovery)

        sut.scanForBpm()

        #expect(discovery.scanForPairingCalls == 1)
    }

    @Test("connectBpm with empty broadcast ID returns invalidBroadcastId")
    func connectBpmEmptyBroadcastId() async {
        let sut = makeSUT()

        let result = await sut.connectBpm(broadcastId: "", userNumber: 1)

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

    @Test("receiveBpmReading with empty broadcast ID returns invalidBroadcastId")
    func receiveBpmReadingEmptyBroadcastId() async {
        let sut = makeSUT()

        let result = await sut.receiveBpmReading(broadcastId: "")

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

    @Test("receiveBpmReading with valid broadcast ID succeeds")
    func receiveBpmReadingSuccess() async {
        let sut = makeSUT()

        let result = await sut.receiveBpmReading(broadcastId: "BPM001")

        switch result {
        case .success:
            break
        case .failure(let error):
            Issue.record("Expected success, got \(error)")
        }
    }

    // MARK: - DeviceCategory Tests

    @Test("DeviceCategory: raw values are correct")
    func deviceCategoryRawValues() {
        #expect(DeviceCategory.scale.rawValue == "scale")
        #expect(DeviceCategory.bpm.rawValue == "bpm")
    }

    // MARK: - BluetoothBpmType Tests

    @Test("BluetoothBpmType: has bpm case")
    func bluetoothBpmTypeCases() {
        #expect(BluetoothBpmType.allCases.count == 1)
        #expect(BluetoothBpmType.bpm.rawValue == "bpm")
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
            babyService: MockBabyService(),
            logger: logger ?? MockLoggerService(),
            discoveryManager: discovery ?? MockBLEDiscoveryManager()
        )
    }
}
