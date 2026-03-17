import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BpmModelTests {

    // MARK: - BpmEntry Tests

    @Test("BpmEntry: init sets all properties")
    func bpmEntryInitSetsAllProperties() {
        let entry = BpmEntry(
            systolic: 120,
            diastolic: 80,
            pulse: 72,
            meanArterial: "93.3",
            note: "Morning reading"
        )

        #expect(entry.systolic == 120)
        #expect(entry.diastolic == 80)
        #expect(entry.pulse == 72)
        #expect(entry.meanArterial == "93.3")
        #expect(entry.note == "Morning reading")
    }

    @Test("BpmEntry: init defaults all properties to nil")
    func bpmEntryInitDefaultsToNil() {
        let entry = BpmEntry()

        #expect(entry.systolic == nil)
        #expect(entry.diastolic == nil)
        #expect(entry.pulse == nil)
        #expect(entry.meanArterial == nil)
        #expect(entry.note == nil)
    }

    @Test("BpmEntry: convenience init from DTO maps values correctly")
    func bpmEntryInitFromDTO() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 130.0,
            diastolic: 85.0,
            pulse: 68.0,
            meanArterial: "100.0",
            note: "Post-exercise",
            irregularHb: false,
            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        let entry = BpmEntry(from: dto)

        #expect(entry.systolic == 130)
        #expect(entry.diastolic == 85)
        #expect(entry.pulse == 68)
        #expect(entry.meanArterial == "100.0")
        #expect(entry.note == "Post-exercise")
    }

    @Test("BpmEntry: convenience init from DTO handles nil values")
    func bpmEntryInitFromDTOHandlesNils() {
        let dto = BpmOperationDTO(
            accountId: nil,
            systolic: nil,
            diastolic: nil,
            pulse: nil,
            meanArterial: nil,
            note: nil,
            irregularHb: nil,
            source: nil,
            unit: nil,
            entryTimestamp: nil,
            operationType: nil,
            serverTimestamp: nil
        )

        let entry = BpmEntry(from: dto)

        #expect(entry.systolic == nil)
        #expect(entry.diastolic == nil)
        #expect(entry.pulse == nil)
        #expect(entry.meanArterial == nil)
        #expect(entry.note == nil)
    }

    // MARK: - BpmMetric Tests

    @Test("BpmMetric: init sets all properties")
    func bpmMetricInitSetsAllProperties() {
        let metric = BpmMetric(
            irregularHb: true,
            source: "device",
            unit: "mmHg"
        )

        #expect(metric.irregularHb == true)
        #expect(metric.source == "device")
        #expect(metric.unit == "mmHg")
    }

    @Test("BpmMetric: init defaults all properties to nil")
    func bpmMetricInitDefaultsToNil() {
        let metric = BpmMetric()

        #expect(metric.irregularHb == nil)
        #expect(metric.source == nil)
        #expect(metric.unit == nil)
    }

    @Test("BpmMetric: convenience init from DTO maps values correctly")
    func bpmMetricInitFromDTO() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 120.0,
            diastolic: 80.0,
            pulse: 72.0,
            meanArterial: nil,
            note: nil,
            irregularHb: true,
            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        let metric = BpmMetric(from: dto)

        #expect(metric.irregularHb == true)
        #expect(metric.source == "manual")
        #expect(metric.unit == "mmHg")
    }

    // MARK: - BpmOperationDTO Tests

    @Test("BpmOperationDTO: Identifiable id uses entryTimestamp")
    func bpmOperationDTOIdentifiable() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 120.0,
            diastolic: 80.0,
            pulse: 72.0,
            meanArterial: nil,
            note: nil,
            irregularHb: nil,
            source: nil,
            unit: nil,
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        #expect(dto.id == "2026-03-01T08:00:00Z")
    }

    @Test("BpmOperationDTO: date parsing from entryTimestamp")
    func bpmOperationDTODateParsing() {
        let dto = BpmOperationDTO(
            accountId: nil,
            systolic: nil,
            diastolic: nil,
            pulse: nil,
            meanArterial: nil,
            note: nil,
            irregularHb: nil,
            source: nil,
            unit: nil,
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: nil,
            serverTimestamp: nil
        )

        #expect(dto.date != nil)
    }

    @Test("BpmOperationDTO: copy with new timestamp preserves other fields")
    func bpmOperationDTOCopy() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 130.0,
            diastolic: 85.0,
            pulse: 68.0,
            meanArterial: "100.0",
            note: "Test",
            irregularHb: true,
            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: "2026-03-01T08:00:01Z"
        )

        let copied = dto.copy(with: "2026-03-02T10:00:00Z")

        #expect(copied.entryTimestamp == "2026-03-02T10:00:00Z")
        #expect(copied.accountId == "acct-1")
        #expect(copied.systolic == 130.0)
        #expect(copied.diastolic == 85.0)
        #expect(copied.pulse == 68.0)
        #expect(copied.meanArterial == "100.0")
        #expect(copied.note == "Test")
        #expect(copied.irregularHb == true)
        #expect(copied.source == "manual")
        #expect(copied.unit == "mmHg")
        #expect(copied.operationType == "create")
        #expect(copied.serverTimestamp == "2026-03-01T08:00:01Z")
    }

    @Test("BpmOperationDTO: toAPIRequest maps fields correctly")
    func bpmOperationDTOToAPIRequest() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 120.0,
            diastolic: 80.0,
            pulse: 72.0,
            meanArterial: "93.3",
            note: "Morning",
            irregularHb: false,
            source: "device",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        let request = dto.toAPIRequest()

        #expect(request.userId == "acct-1")
        #expect(request.systolic == 120.0)
        #expect(request.diastolic == 80.0)
        #expect(request.pulse == 72.0)
        #expect(request.meanArterial == "93.3")
        #expect(request.note == "Morning")
        #expect(request.irregularHb == false)
        #expect(request.source == "device")
        #expect(request.unit == "mmHg")
        #expect(request.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(request.operationType == "create")
    }

    // MARK: - Entry + BPM Relationship Tests

    @Test("Entry: init from BpmOperationDTO sets deviceType to bpm")
    func entryInitFromBpmDTO() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 120.0,
            diastolic: 80.0,
            pulse: 72.0,
            meanArterial: "93.3",
            note: "Test",
            irregularHb: false,
            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: "2026-03-01T08:00:01Z"
        )

        let entry = Entry(from: dto, accountId: "acct-1", isSynced: true)

        #expect(entry.accountId == "acct-1")
        #expect(entry.deviceType == "bpm")
        #expect(entry.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(entry.operationType == "create")
        #expect(entry.serverTimestamp == "2026-03-01T08:00:01Z")
        #expect(entry.isSynced == true)
        #expect(entry.bpmEntry != nil)
        #expect(entry.bpmEntry?.systolic == 120)
        #expect(entry.bpmEntry?.diastolic == 80)
        #expect(entry.bpmEntry?.pulse == 72)
        #expect(entry.bpmEntry?.meanArterial == "93.3")
        #expect(entry.bpmEntry?.note == "Test")
        #expect(entry.bpmEntryMetric != nil)
        #expect(entry.bpmEntryMetric?.irregularHb == false)
        #expect(entry.bpmEntryMetric?.source == "manual")
        #expect(entry.bpmEntryMetric?.unit == "mmHg")
    }

    @Test("Entry: toBpmOperationDTO round-trips data correctly")
    func entryToBpmOperationDTORoundTrip() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 130.0,
            diastolic: 85.0,
            pulse: 68.0,
            meanArterial: "100.0",
            note: "Evening",
            irregularHb: true,
            source: "device",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T20:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        let entry = Entry(from: dto, accountId: "acct-1")
        let result = entry.toBpmOperationDTO()

        #expect(result.accountId == "acct-1")
        #expect(result.systolic == 130.0)
        #expect(result.diastolic == 85.0)
        #expect(result.pulse == 68.0)
        #expect(result.meanArterial == "100.0")
        #expect(result.note == "Evening")
        #expect(result.irregularHb == true)
        #expect(result.source == "device")
        #expect(result.unit == "mmHg")
        #expect(result.entryTimestamp == "2026-03-01T20:00:00Z")
        #expect(result.operationType == "create")
    }

    @Test("Entry: bpmEntry relationship is nil by default for scale entries")
    func entryBpmRelationshipNilForScale() {
        let entry = Entry(
            entryTimestamp: "2026-03-01T08:00:00Z",
            accountId: "acct-1",
            operationType: "create"
        )

        #expect(entry.bpmEntry == nil)
        #expect(entry.bpmEntryMetric == nil)
    }

    @Test("Entry: scale and bpm relationships are independent")
    func entryScaleAndBpmRelationshipsIndependent() {
        let entry = Entry(
            entryTimestamp: "2026-03-01T08:00:00Z",
            accountId: "acct-1",
            operationType: "create",
            deviceType: "bpm"
        )

        entry.bpmEntry = BpmEntry(systolic: 120, diastolic: 80, pulse: 72)
        entry.scaleEntry = BathScaleEntry(weight: 1800)

        #expect(entry.bpmEntry?.systolic == 120)
        #expect(entry.scaleEntry?.weight == 1800)
    }
}
