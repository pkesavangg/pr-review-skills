// Coverage target: 85% (Domain model extensions)

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HistoryEntryFormattingTests {

    // MARK: - BPHistoryEntry.hasNotes

    @Test("BPHistoryEntry hasNotes returns true when notes present")
    func bpEntry_hasNotes_withNotes_returnsTrue() {
        let entry = makeBPEntry(notes: "Some note")
        #expect(entry.hasNotes == true)
    }

    @Test("BPHistoryEntry hasNotes returns false when notes nil")
    func bpEntry_hasNotes_withNil_returnsFalse() {
        let entry = makeBPEntry(notes: nil)
        #expect(entry.hasNotes == false)
    }

    @Test("BPHistoryEntry hasNotes returns false when notes empty string")
    func bpEntry_hasNotes_withEmptyString_returnsFalse() {
        let entry = makeBPEntry(notes: "")
        #expect(entry.hasNotes == false)
    }

    // MARK: - BPHistoryEntry.pressureText

    @Test("BPHistoryEntry pressureText formats as systolic/diastolic")
    func bpEntry_pressureText_formatsCorrectly() {
        let entry = makeBPEntry(systolic: 120, diastolic: 80)
        #expect(entry.pressureText == "120/80")
    }

    // MARK: - BabyHistoryEntry.hasNotes

    @Test("BabyHistoryEntry hasNotes returns true when notes present")
    func babyEntry_hasNotes_withNotes_returnsTrue() {
        let entry = makeBabyEntry(notes: "Fed well today")
        #expect(entry.hasNotes == true)
    }

    @Test("BabyHistoryEntry hasNotes returns false when notes nil")
    func babyEntry_hasNotes_withNil_returnsFalse() {
        let entry = makeBabyEntry(notes: nil)
        #expect(entry.hasNotes == false)
    }

    @Test("BabyHistoryEntry hasNotes returns false when notes empty string")
    func babyEntry_hasNotes_withEmptyString_returnsFalse() {
        let entry = makeBabyEntry(notes: "")
        #expect(entry.hasNotes == false)
    }

    // MARK: - BabyHistoryEntry.weightText

    @Test("BabyHistoryEntry weightText formats as lbs and oz")
    func babyEntry_weightText_formatsCorrectly() {
        let entry = makeBabyEntry(weightLbs: 8, weightOz: 5.2)
        #expect(entry.weightText == "8 lbs 5.2 oz")
    }

    // MARK: - BabyHistoryEntry.lengthText

    @Test("BabyHistoryEntry lengthText formats as integer inches")
    func babyEntry_lengthText_formatsCorrectly() {
        let entry = makeBabyEntry(lengthInches: 20.7)
        #expect(entry.lengthText == "20 in")
    }

    // MARK: - BabyHistoryEntry.percentileText

    @Test("BabyHistoryEntry percentileText appends th suffix")
    func babyEntry_percentileText_formatsCorrectly() {
        let entry = makeBabyEntry(percentile: 75)
        #expect(entry.percentileText == "75 th")
    }

    // MARK: - BPHistoryMonth.pressureText

    @Test("BPHistoryMonth pressureText formats as avgSystolic/avgDiastolic")
    func bpMonth_pressureText_formatsCorrectly() {
        let month = makeBPMonth(avgSystolic: 130, avgDiastolic: 85)
        #expect(month.pressureText == "130/85")
    }

    @Test("BPHistoryMonth avgPulse is a raw integer value with no prefix")
    func bpMonth_avgPulse_isRawValue() {
        let month = makeBPMonth(avgPulse: 72)
        #expect(month.avgPulse == 72)
    }
}

// MARK: - Fixtures

@MainActor
private func makeBPEntry(
    systolic: Int = 120,
    diastolic: Int = 80,
    pulse: Int = 72,
    notes: String? = nil
) -> BPHistoryEntry {
    BPHistoryEntry(
        id: UUID(),
        entryTimestamp: "2026-03-27T10:00:00Z",
        systolic: systolic,
        diastolic: diastolic,
        pulse: pulse,
        notes: notes
    )
}

@MainActor
private func makeBabyEntry(
    weightLbs: Int = 8,
    weightOz: Double = 5.0,
    weightKg: Double = 3.6,
    weightLb: Double = 8.31,
    lengthInches: Double = 20.0,
    lengthCm: Double = 50.8,
    percentile: Int = 50,
    notes: String? = nil
) -> BabyHistoryEntry {
    BabyHistoryEntry(
        id: UUID(),
        entryTimestamp: "2026-03-27T10:00:00Z",
        weightLbs: weightLbs,
        weightOz: weightOz,
        weightKg: weightKg,
        weightLb: weightLb,
        lengthInches: lengthInches,
        lengthCm: lengthCm,
        percentile: percentile,
        notes: notes,
        weightDisplay: "\(weightLbs) lbs \(weightOz) oz",
        lengthDisplay: "\(Int(lengthInches)) in"
    )
}

@MainActor
private func makeBPMonth(
    avgSystolic: Int = 120,
    avgDiastolic: Int = 80,
    avgPulse: Int = 72
) -> BPHistoryMonth {
    BPHistoryMonth(
        id: "2026-03",
        count: 10,
        avgSystolic: avgSystolic,
        avgDiastolic: avgDiastolic,
        avgPulse: avgPulse,
        month: "03",
        year: "2026"
    )
}
