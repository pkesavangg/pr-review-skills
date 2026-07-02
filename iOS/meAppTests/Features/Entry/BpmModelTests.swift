import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BpmModelTests {

    // MARK: - BathScaleEntry BPM Fields Tests

    @Test("BathScaleEntry: init sets BPM properties")
    func bathScaleEntryInitSetsBpmProperties() {
        let entry = BathScaleEntry(
            source: "manual",
            systolic: 120,
            diastolic: 80,
            meanArterial: "93.3"
        )

        #expect(entry.systolic == 120)
        #expect(entry.diastolic == 80)
        #expect(entry.meanArterial == "93.3")
        #expect(entry.source == "manual")
    }

    @Test("BathScaleEntry: BPM fields default to nil")
    func bathScaleEntryBpmFieldsDefaultToNil() {
        let entry = BathScaleEntry()

        #expect(entry.systolic == nil)
        #expect(entry.diastolic == nil)
        #expect(entry.meanArterial == nil)
    }

    @Test("BathScaleEntry: convenience init from BpmOperationDTO maps values correctly")
    func bathScaleEntryInitFromBpmDTO() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 130.0,
            diastolic: 85.0,
            pulse: 68.0,
            meanArterial: "100.0",
            note: "Post-exercise",
            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        let entry = BathScaleEntry(from: dto)

        #expect(entry.systolic == 130)
        #expect(entry.diastolic == 85)
        #expect(entry.meanArterial == "100.0")
        #expect(entry.source == "manual")
    }

    @Test("BathScaleEntry: convenience init from BpmOperationDTO handles nil values")
    func bathScaleEntryInitFromBpmDTOHandlesNils() {
        let dto = BpmOperationDTO(
            accountId: nil,
            systolic: nil,
            diastolic: nil,
            pulse: nil,
            meanArterial: nil,
            note: nil,

            source: nil,
            unit: nil,
            entryTimestamp: nil,
            operationType: nil,
            serverTimestamp: nil
        )

        let entry = BathScaleEntry(from: dto)

        #expect(entry.systolic == nil)
        #expect(entry.diastolic == nil)
        #expect(entry.meanArterial == nil)
        #expect(entry.source == nil)
    }

    // MARK: - BathScaleMetric BPM Fields Tests

    @Test("BathScaleMetric: convenience init from BpmOperationDTO maps pulse and unit")
    func bathScaleMetricInitFromBpmDTO() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 120.0,
            diastolic: 80.0,
            pulse: 72.0,
            meanArterial: nil,
            note: nil,

            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: nil
        )

        let metric = BathScaleMetric(from: dto)

        #expect(metric.pulse == 72)
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
        #expect(copied.source == "manual")
        #expect(copied.unit == "mmHg")
        #expect(copied.operationType == "create")
        #expect(copied.serverTimestamp == "2026-03-01T08:00:01Z")
    }

    // MARK: - Entry + BPM Relationship Tests

    @Test("Entry: init from BpmOperationDTO sets deviceType to bpm and uses scaleEntry")
    func entryInitFromBpmDTO() {
        let dto = BpmOperationDTO(
            accountId: "acct-1",
            systolic: 120.0,
            diastolic: 80.0,
            pulse: 72.0,
            meanArterial: "93.3",
            note: "Test",
            source: "manual",
            unit: "mmHg",
            entryTimestamp: "2026-03-01T08:00:00Z",
            operationType: "create",
            serverTimestamp: "2026-03-01T08:00:01Z"
        )

        let entry = Entry(from: dto, accountId: "acct-1", isSynced: true)

        #expect(entry.accountId == "acct-1")
        #expect(entry.entryType == "bpm")
        #expect(entry.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(entry.operationType == "create")
        #expect(entry.serverTimestamp == "2026-03-01T08:00:01Z")
        #expect(entry.isSynced == true)
        #expect(entry.scaleEntry != nil)
        #expect(entry.scaleEntry?.systolic == 120)
        #expect(entry.scaleEntry?.diastolic == 80)
        #expect(entry.scaleEntry?.meanArterial == "93.3")
        #expect(entry.note == "Test")
        #expect(entry.scaleEntry?.source == "manual")
        #expect(entry.scaleEntryMetric != nil)
        #expect(entry.scaleEntryMetric?.pulse == 72)
        #expect(entry.scaleEntryMetric?.unit == "mmHg")
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
        #expect(result.source == "device")
        #expect(result.unit == "mmHg")
        #expect(result.entryTimestamp == "2026-03-01T20:00:00Z")
        #expect(result.operationType == "create")
    }

    @Test("Entry: scaleEntry is nil by default")
    func entryScaleEntryNilByDefault() {
        let entry = Entry(
            entryTimestamp: "2026-03-01T08:00:00Z",
            accountId: "acct-1",
            operationType: "create"
        )

        #expect(entry.scaleEntry == nil)
        #expect(entry.scaleEntryMetric == nil)
    }
}
