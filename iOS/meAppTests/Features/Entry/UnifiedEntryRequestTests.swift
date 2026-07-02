import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
struct UnifiedEntryRequestTests {

    // MARK: - Helpers

    private func makeDTO(
        entryType: String?,
        operationType: String = OperationType.create.rawValue,
        weight: Double? = nil,
        bodyFat: Double? = nil,
        muscleMass: Double? = nil,
        water: Double? = nil,
        bmi: Double? = nil,
        boneMass: Double? = nil,
        impedance: Double? = nil,
        pulse: Double? = nil,
        systolic: Double? = nil,
        diastolic: Double? = nil,
        unit: String? = nil,
        source: String? = nil,
        timestamp: String = "2026-05-06T10:00:00Z"
    ) -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: bmi,
            bodyFat: bodyFat,
            boneMass: boneMass,
            entryTimestamp: timestamp,
            entryType: entryType,
            impedance: impedance,
            metabolicAge: nil,
            muscleMass: muscleMass,
            operationType: operationType,
            proteinPercent: nil,
            pulse: pulse,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: source,
            subcutaneousFatPercent: nil,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: nil,
            unit: unit,
            visceralFatLevel: nil,
            water: water,
            weight: weight
        )
    }

    // MARK: - EntryCategory mapping

    @Test("EntryCategory maps each EntryType onto the wire category")
    func entryCategoryFromEntryType() {
        #expect(EntryCategory(entryType: .scale) == .weight)
        #expect(EntryCategory(entryType: .bpm) == .bp)
        #expect(EntryCategory(entryType: .baby) == .baby)
    }

    // MARK: - Weight create

    @Test("weight create: maps all weight fields and converts Double values to Int")
    func weightCreateMapsFields() throws {
        let dto = makeDTO(
            entryType: EntryType.scale.rawValue,
            weight: 1723,
            bodyFat: 225,
            muscleMass: 401,
            water: 552,
            bmi: 243,
            boneMass: 38,
            impedance: 495,
            pulse: 68,
            unit: "lb",
            source: "btWifiR4"
        )
        let request = try #require(UnifiedEntryRequest(from: dto))

        #expect(request.category == EntryCategory.weight.rawValue)
        #expect(request.operationType == OperationType.create.rawValue)
        #expect(request.entryTimestamp == "2026-05-06T10:00:00Z")
        #expect(request.weight == 1723)
        #expect(request.bodyFat == 225)
        #expect(request.muscleMass == 401)
        #expect(request.water == 552)
        #expect(request.bmi == 243)
        #expect(request.boneMass == 38)
        #expect(request.impedance == 495)
        #expect(request.pulse == 68)
        #expect(request.unit == "lb")
        #expect(request.source == "btWifiR4")
        // BP-only fields stay nil for a weight entry.
        #expect(request.systolic == nil)
        #expect(request.diastolic == nil)
        #expect(request.note == nil)
    }

    @Test("nil entryType defaults to weight category")
    func nilEntryTypeDefaultsToWeight() throws {
        let dto = makeDTO(entryType: nil, weight: 1800)
        let request = try #require(UnifiedEntryRequest(from: dto))
        #expect(request.category == EntryCategory.weight.rawValue)
        #expect(request.weight == 1800)
    }

    // MARK: - Weight delete

    @Test("weight delete: carries only discriminating fields, strips measurements")
    func weightDeleteStripsMeasurements() throws {
        let dto = makeDTO(
            entryType: EntryType.scale.rawValue,
            operationType: OperationType.delete.rawValue,
            weight: 1723,
            bodyFat: 225,
            unit: "lb",
            source: "btWifiR4"
        )
        let request = try #require(UnifiedEntryRequest(from: dto))

        #expect(request.category == EntryCategory.weight.rawValue)
        #expect(request.operationType == OperationType.delete.rawValue)
        #expect(request.entryTimestamp == "2026-05-06T10:00:00Z")
        #expect(request.weight == nil)
        #expect(request.bodyFat == nil)
        #expect(request.unit == nil)
        #expect(request.source == nil)
    }

    // MARK: - BP create

    @Test("bp create: maps systolic, diastolic, pulse, source and note")
    func bpCreateMapsFields() throws {
        let dto = makeDTO(
            entryType: EntryType.bpm.rawValue,
            pulse: 72,
            systolic: 120,
            diastolic: 80,
            source: "manual"
        )
        let request = try #require(UnifiedEntryRequest(from: dto, note: "Morning reading"))

        #expect(request.category == EntryCategory.bp.rawValue)
        #expect(request.operationType == OperationType.create.rawValue)
        #expect(request.systolic == 120)
        #expect(request.diastolic == 80)
        #expect(request.pulse == 72)
        #expect(request.source == "manual")
        #expect(request.note == "Morning reading")
        // Weight-only fields stay nil for a BP entry.
        #expect(request.weight == nil)
        #expect(request.bmi == nil)
    }

    @Test("bp delete: strips measurements and note")
    func bpDeleteStripsFields() throws {
        let dto = makeDTO(
            entryType: EntryType.bpm.rawValue,
            operationType: OperationType.delete.rawValue,
            pulse: 72,
            systolic: 120,
            diastolic: 80,
            source: "manual"
        )
        let request = try #require(UnifiedEntryRequest(from: dto, note: "should be dropped"))

        #expect(request.category == EntryCategory.bp.rawValue)
        #expect(request.operationType == OperationType.delete.rawValue)
        #expect(request.systolic == nil)
        #expect(request.diastolic == nil)
        #expect(request.pulse == nil)
        #expect(request.source == nil)
        #expect(request.note == nil)
    }

    // MARK: - Baby out of scope

    @Test("baby category returns nil — out of scope for MOB-384 write path")
    func babyReturnsNil() {
        let dto = makeDTO(entryType: EntryType.baby.rawValue, weight: 4520)
        #expect(UnifiedEntryRequest(from: dto) == nil)
    }

    // MARK: - Encoding

    @Test("encoding strips nil fields so the wire payload carries only relevant keys")
    func encodingStripsNils() throws {
        let request = UnifiedEntryRequest(
            category: EntryCategory.weight.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-05-06T10:00:00Z",
            weight: 1723,
            source: "manual"
        )
        let data = try JSONEncoder().encode(request)
        let json = try #require(String(data: data, encoding: .utf8))

        #expect(json.contains("\"weight\":1723"))
        #expect(json.contains("\"category\":\"weight\""))
        #expect(json.contains("\"source\":\"manual\""))
        // nil optionals must be omitted entirely.
        #expect(!json.contains("systolic"))
        #expect(!json.contains("diastolic"))
        #expect(!json.contains("note"))
        #expect(!json.contains("bodyFat"))
    }

    @Test("array of one encodes as a raw JSON array")
    func arrayOfOneEncodesAsArray() throws {
        let request = UnifiedEntryRequest(
            category: EntryCategory.bp.rawValue,
            operationType: OperationType.create.rawValue,
            entryTimestamp: "2026-05-06T10:00:00Z",
            systolic: 120,
            diastolic: 80,
            pulse: 72,
            source: "manual"
        )
        let data = try JSONEncoder().encode([request])
        let json = try #require(String(data: data, encoding: .utf8))
        #expect(json.hasPrefix("["))
        #expect(json.hasSuffix("]"))
    }
}
