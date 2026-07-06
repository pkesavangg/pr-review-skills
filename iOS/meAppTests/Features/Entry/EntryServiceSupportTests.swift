import Foundation
@testable import meApp
import Testing

/// Unit tests for the pure value types in `EntryServiceSupport.swift`
/// (`EntryMetricAccumulator`, `EntrySummaryBucket`, `EntriesPagination`, `EntriesPage`).
/// These are dependency-free aggregation/paging helpers used by `EntryService`'s
/// summary and cursor-read paths, so their guard/edge branches are exercised here directly.
@Suite(.serialized)
struct EntryServiceSupportTests {

    // MARK: - Helpers

    /// Builds a `BathScaleOperationDTO` with all metric fields defaulting to `nil`.
    /// `var`-optional fields (serverEntryId/babyId/…) take their synthesized `nil` defaults.
    private func makeDTO( // swiftlint:disable:this function_default_parameter_at_end
        entryTimestamp: String? = nil,
        weight: Double? = nil,
        bodyFat: Double? = nil,
        muscleMass: Double? = nil,
        water: Double? = nil,
        bmi: Double? = nil,
        bmr: Double? = nil,
        metabolicAge: Double? = nil,
        proteinPercent: Double? = nil,
        pulse: Double? = nil,
        skeletalMusclePercent: Double? = nil,
        subcutaneousFatPercent: Double? = nil,
        visceralFatLevel: Double? = nil,
        boneMass: Double? = nil,
        impedance: Double? = nil,
        systolic: Double? = nil,
        diastolic: Double? = nil,
        meanArterial: Double? = nil
    ) -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: bmr,
            bmi: bmi,
            bodyFat: bodyFat,
            boneMass: boneMass,
            entryTimestamp: entryTimestamp,
            entryType: nil,
            impedance: impedance,
            metabolicAge: metabolicAge,
            muscleMass: muscleMass,
            operationType: nil,
            proteinPercent: proteinPercent,
            pulse: pulse,
            serverTimestamp: nil,
            skeletalMusclePercent: skeletalMusclePercent,
            source: nil,
            subcutaneousFatPercent: subcutaneousFatPercent,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: meanArterial,
            unit: nil,
            visceralFatLevel: visceralFatLevel,
            water: water,
            weight: weight
        )
    }

    // MARK: - EntryMetricAccumulator

    @Test("EntryMetricAccumulator average of positive values")
    func metricAccumulatorAveragesPositiveValues() {
        var acc = EntryMetricAccumulator()
        acc.add(10)
        acc.add(20)
        acc.add(30)

        #expect(acc.average == 20)
    }

    @Test("EntryMetricAccumulator ignores nil, zero, and negative values")
    func metricAccumulatorIgnoresNonPositive() {
        var acc = EntryMetricAccumulator()
        acc.add(nil)
        acc.add(0)
        acc.add(-5)
        acc.add(8)

        // Only the single positive value counts.
        #expect(acc.average == 8)
    }

    @Test("EntryMetricAccumulator average is nil when empty")
    func metricAccumulatorEmptyAverageIsNil() {
        let acc = EntryMetricAccumulator()

        #expect(acc.average == nil)
    }

    @Test("EntryMetricAccumulator average is nil when only non-positive values added")
    func metricAccumulatorOnlyNonPositiveAverageIsNil() {
        var acc = EntryMetricAccumulator()
        acc.add(0)
        acc.add(nil)

        #expect(acc.average == nil)
    }

    // MARK: - EntrySummaryBucket

    @Test("EntrySummaryBucket aggregates weight and tracks latest timestamp")
    func summaryBucketAggregatesWeight() {
        var bucket = EntrySummaryBucket(accountId: "acct-1", period: "2026-05")
        bucket.add(dto: makeDTO(entryTimestamp: "2026-05-01T10:00:00Z", weight: 100))
        bucket.add(dto: makeDTO(entryTimestamp: "2026-05-03T10:00:00Z", weight: 200))

        #expect(bucket.count == 2)
        #expect(bucket.weightCount == 2)
        #expect(bucket.weightSum == 300)
        #expect(bucket.averagedWeight == 150)
        #expect(bucket.latestTimestamp == "2026-05-03T10:00:00Z")
    }

    @Test("EntrySummaryBucket skips non-positive weight but still counts the entry")
    func summaryBucketSkipsNonPositiveWeight() {
        var bucket = EntrySummaryBucket(accountId: "acct-1", period: "2026-05")
        bucket.add(dto: makeDTO(entryTimestamp: "2026-05-01T10:00:00Z", weight: 0))
        bucket.add(dto: makeDTO(entryTimestamp: "2026-05-02T10:00:00Z", weight: nil))

        #expect(bucket.count == 2)
        #expect(bucket.weightCount == 0)
        #expect(bucket.averagedWeight == 0)
    }

    @Test("EntrySummaryBucket averagedWeight is zero with no weighted entries")
    func summaryBucketAveragedWeightZeroWhenEmpty() {
        let bucket = EntrySummaryBucket(accountId: "acct-1", period: "2026-05")

        #expect(bucket.averagedWeight == 0)
    }

    @Test("EntrySummaryBucket accumulates body-composition and BP metrics")
    func summaryBucketAccumulatesMetrics() {
        var bucket = EntrySummaryBucket(accountId: "acct-1", period: "2026-05")
        bucket.add(dto: makeDTO(
            entryTimestamp: "2026-05-01T10:00:00Z",
            weight: 100,
            bodyFat: 20,
            muscleMass: 40,
            water: 50,
            bmi: 22,
            bmr: 1500,
            metabolicAge: 30,
            proteinPercent: 18,
            pulse: 60,
            skeletalMusclePercent: 35,
            subcutaneousFatPercent: 15,
            visceralFatLevel: 8,
            boneMass: 3,
            impedance: 500,
            systolic: 120,
            diastolic: 80,
            meanArterial: 93
        ))

        #expect(bucket.bodyFat.average == 20)
        #expect(bucket.muscleMass.average == 40)
        #expect(bucket.water.average == 50)
        #expect(bucket.bmi.average == 22)
        #expect(bucket.bmr.average == 1500)
        #expect(bucket.metabolicAge.average == 30)
        #expect(bucket.proteinPercent.average == 18)
        #expect(bucket.pulse.average == 60)
        #expect(bucket.skeletalMusclePercent.average == 35)
        #expect(bucket.subcutaneousFatPercent.average == 15)
        #expect(bucket.visceralFatLevel.average == 8)
        #expect(bucket.boneMass.average == 3)
        #expect(bucket.impedance.average == 500)
        #expect(bucket.systolic.average == 120)
        #expect(bucket.diastolic.average == 80)
        #expect(bucket.meanArterial.average == 93)
    }

    @Test("EntrySummaryBucket keeps the earliest timestamp when later entry is older")
    func summaryBucketLatestTimestampKeepsMax() {
        var bucket = EntrySummaryBucket(accountId: "acct-1", period: "2026-05")
        bucket.add(dto: makeDTO(entryTimestamp: "2026-05-10T10:00:00Z", weight: 100))
        bucket.add(dto: makeDTO(entryTimestamp: "2026-05-02T10:00:00Z", weight: 100))

        #expect(bucket.latestTimestamp == "2026-05-10T10:00:00Z")
    }

    // MARK: - EntriesPagination

    @Test("EntriesPagination clamp keeps in-range limits unchanged")
    func paginationClampInRange() {
        #expect(EntriesPagination.clamp(limit: 20) == 20)
        #expect(EntriesPagination.clamp(limit: 1) == 1)
        #expect(EntriesPagination.clamp(limit: EntriesPagination.maxLimit) == EntriesPagination.maxLimit)
    }

    @Test("EntriesPagination clamp raises below-minimum limits to 1")
    func paginationClampBelowMinimum() {
        #expect(EntriesPagination.clamp(limit: 0) == 1)
        #expect(EntriesPagination.clamp(limit: -10) == 1)
    }

    @Test("EntriesPagination clamp lowers above-maximum limits to maxLimit")
    func paginationClampAboveMaximum() {
        #expect(EntriesPagination.clamp(limit: EntriesPagination.maxLimit + 1) == EntriesPagination.maxLimit)
        #expect(EntriesPagination.clamp(limit: 10_000) == EntriesPagination.maxLimit)
    }

    @Test("EntriesPagination default and max constants")
    func paginationConstants() {
        #expect(EntriesPagination.defaultLimit == 20)
        #expect(EntriesPagination.maxLimit == 100)
    }

    // MARK: - EntriesPage

    @Test("EntriesPage.empty has no entries, no cursor, and no more pages")
    func entriesPageEmpty() {
        let page = EntriesPage.empty

        #expect(page.entries.isEmpty)
        #expect(page.nextCursor == nil)
        #expect(page.hasMore == false)
    }

    @Test("EntriesPage equatability")
    func entriesPageEquatable() {
        let dto = makeDTO(entryTimestamp: "2026-05-01T10:00:00Z", weight: 100)
        let first = EntriesPage(entries: [dto], nextCursor: "2026-05-01T10:00:00Z", hasMore: true)
        let second = EntriesPage(entries: [dto], nextCursor: "2026-05-01T10:00:00Z", hasMore: true)

        #expect(first == second)
        #expect(first != EntriesPage.empty)
    }
}
