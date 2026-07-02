import Foundation
@testable import meApp
import Testing

// MARK: - BpmReadingDisplayData Tests

@Suite(.serialized)
@MainActor
struct BpmReadingDisplayDataTests {

    // MARK: - Helpers

    private func makeSummary(
        systolic: Double?,
        diastolic: Double?,
        pulse: Double? = 70,
        timestamp: String = "2026-03-01T08:00:00Z"
    ) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct-bpm",
            period: "2026-03-01",
            entryTimestamp: timestamp,
            date: DateTimeTools.getDateFromDateString("2026-03-01", format: "yyyy-MM-dd"),
            count: 1,
            weight: 0,
            pulse: pulse,
            systolic: systolic,
            diastolic: diastolic
        )
    }

    // MARK: - init?(entry:)

    @Test("init from entry returns nil when systolic is missing")
    func initFromEntryNilWhenSystolicMissing() {
        let entry = EntryTestFixtures.makeBpmEntry(systolic: nil, diastolic: 80)
        let data = BpmReadingDisplayData(entry: entry)
        #expect(data == nil)
    }

    @Test("init from entry returns nil when diastolic is missing")
    func initFromEntryNilWhenDiastolicMissing() {
        let entry = EntryTestFixtures.makeBpmEntry(systolic: 120, diastolic: nil)
        let data = BpmReadingDisplayData(entry: entry)
        #expect(data == nil)
    }

    @Test("init from entry copies reading fields and metadata")
    func initFromEntryCopiesFieldsAndMetadata() {
        let id = UUID()
        let entry = EntryTestFixtures.makeBpmEntry(
            id: id,
            timestamp: "2026-03-11T09:30:00Z",
            systolic: 128,
            diastolic: 84,
            pulse: 67
        )

        let data = BpmReadingDisplayData(entry: entry)

        #expect(data != nil)
        #expect(data?.id == id)
        #expect(data?.systolic == 128)
        #expect(data?.diastolic == 84)
        #expect(data?.pulse == 67)
        #expect(data?.timestamp == "2026-03-11T09:30:00Z")
    }

    @Test("init from entry defaults pulse to 0 when metric pulse is missing")
    func initFromEntryDefaultsPulseZero() {
        let entry = EntryTestFixtures.makeBpmEntry(systolic: 120, diastolic: 80, pulse: nil)
        let data = BpmReadingDisplayData(entry: entry)
        #expect(data?.pulse == 0)
    }

    @Test("init from entry sets classification from AHA thresholds")
    func initFromEntryClassification() {
        let entry = EntryTestFixtures.makeBpmEntry(systolic: 182, diastolic: 90)
        let data = BpmReadingDisplayData(entry: entry)
        #expect(data?.classification == .hypertensiveCrisis)
    }

    // MARK: - init?(summary:)

    @Test("init from summary returns nil when systolic is missing")
    func initFromSummaryNilWhenSystolicMissing() {
        let summary = makeSummary(systolic: nil, diastolic: 80)
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data == nil)
    }

    @Test("init from summary returns nil when diastolic is missing")
    func initFromSummaryNilWhenDiastolicMissing() {
        let summary = makeSummary(systolic: 120, diastolic: nil)
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data == nil)
    }

    @Test("init from summary succeeds with valid systolic and diastolic")
    func initFromSummarySucceeds() {
        let summary = makeSummary(systolic: 120, diastolic: 80, pulse: 72)
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data != nil)
        #expect(data?.systolic == 120)
        #expect(data?.diastolic == 80)
        #expect(data?.pulse == 72)
    }

    @Test("init from summary rounds fractional systolic/diastolic values")
    func initFromSummaryRoundsFractionalValues() {
        let summary = makeSummary(systolic: 120.6, diastolic: 79.4, pulse: 71.9)
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data?.systolic == 121)
        #expect(data?.diastolic == 79)
        #expect(data?.pulse == 72)
    }

    @Test("init from summary defaults pulse to 0 when nil")
    func initFromSummaryDefaultsPulseZero() {
        let summary = makeSummary(systolic: 120, diastolic: 80, pulse: nil)
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data?.pulse == 0)
    }

    @Test("init from summary sets classification from AhaPressureClass")
    func initFromSummaryClassification() {
        let summary = makeSummary(systolic: 120, diastolic: 75) // elevated
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data?.classification == .elevated)
    }

    @Test("init from summary uses summary id")
    func initFromSummaryUsesId() {
        let id = UUID()
        let summary = BathScaleWeightSummary(
            id: id,
            accountId: "acct-bpm",
            period: "2026-03-01",
            entryTimestamp: "2026-03-01T08:00:00Z",
            date: Date(),
            count: 1,
            weight: 0,
            systolic: 120,
            diastolic: 80
        )
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data?.id == id)
    }

    // MARK: - formattedDate

    @Test("formattedDate formats ISO timestamp to readable date string")
    func formattedDateFormatsTimestamp() {
        let summary = makeSummary(systolic: 120, diastolic: 80, timestamp: "2026-03-15T08:00:00Z")
        let data = BpmReadingDisplayData(summary: summary)
        let formatted = data?.formattedDate ?? ""
        #expect(formatted.contains("2026"))
        #expect(!formatted.isEmpty)
    }

    @Test("formattedDate returns raw timestamp when parsing fails")
    func formattedDateFallbackToRaw() {
        let summary = makeSummary(systolic: 120, diastolic: 80, timestamp: "not-a-date")
        let data = BpmReadingDisplayData(summary: summary)
        #expect(data?.formattedDate == "not-a-date")
    }

    // MARK: - Classification integration

    @Test("normal reading produces normal classification")
    func normalReading() {
        let data = BpmReadingDisplayData(summary: makeSummary(systolic: 115, diastolic: 75))
        #expect(data?.classification == .normal)
    }

    @Test("elevated systolic produces elevated classification")
    func elevatedReading() {
        let data = BpmReadingDisplayData(summary: makeSummary(systolic: 125, diastolic: 75))
        #expect(data?.classification == .elevated)
    }

    @Test("stage1 hypertension is classified correctly")
    func hypertensionStage1Reading() {
        let data = BpmReadingDisplayData(summary: makeSummary(systolic: 135, diastolic: 85))
        #expect(data?.classification == .hypertensionStage1)
    }

    @Test("stage2 hypertension is classified correctly")
    func hypertensionStage2Reading() {
        let data = BpmReadingDisplayData(summary: makeSummary(systolic: 145, diastolic: 92))
        #expect(data?.classification == .hypertensionStage2)
    }

    @Test("hypertensive crisis is classified correctly")
    func hypertensiveCrisisReading() {
        let data = BpmReadingDisplayData(summary: makeSummary(systolic: 185, diastolic: 125))
        #expect(data?.classification == .hypertensiveCrisis)
    }
}

// MARK: - BpmSummaryCardFooter Tests

@Suite(.serialized)
@MainActor
struct BpmSummaryCardFooterTests {

    @Test("centered case stores the label string")
    func centeredStoresLabel() {
        let footer = BpmSummaryCardFooter.centered("Average")
        if case .centered(let label) = footer {
            #expect(label == "Average")
        } else {
            Issue.record("Expected .centered case")
        }
    }

    @Test("split case stores left and right strings")
    func splitStoresLeftAndRight() {
        let footer = BpmSummaryCardFooter.split(left: "Low", right: "High")
        if case .split(let left, let right) = footer {
            #expect(left == "Low")
            #expect(right == "High")
        } else {
            Issue.record("Expected .split case")
        }
    }

    @Test("centered and split are distinct cases")
    func centeredAndSplitDistinct() {
        let centered = BpmSummaryCardFooter.centered("x")
        let split = BpmSummaryCardFooter.split(left: "x", right: "y")

        if case .centered = centered { } else {
            Issue.record("Expected centered")
        }
        if case .split = split { } else {
            Issue.record("Expected split")
        }
    }
}

// MARK: - GraphDataPreparer BPM Tests

@Suite(.serialized)
@MainActor
struct GraphDataPreparerBpmTests {

    private func makePreparer() -> GraphDataPreparer { GraphDataPreparer() }

    private func makeBpmSummary(
        systolic: Double,
        diastolic: Double,
        pulse: Double,
        date: Date
    ) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "bpm-acct",
            period: DateTimeTools.formatter("yyyy-MM-dd").string(from: date),
            entryTimestamp: ISO8601DateFormatter().string(from: date),
            date: date,
            count: 1,
            weight: 0,
            pulse: pulse,
            systolic: systolic,
            diastolic: diastolic
        )
    }

    // MARK: - buildBpmChartSeries (week)

    @Test("buildBpmChartSeries returns empty for empty operations")
    func bpmChartSeriesEmptyForNoOps() {
        let sut = makePreparer()
        let result = sut.buildBpmChartSeries(from: [], period: .week)
        #expect(result.isEmpty)
    }

    @Test("buildBpmChartSeries produces systolic, diastolic, pulse series for week")
    func bpmChartSeriesProducesThreeSeriesForWeek() {
        let sut = makePreparer()
        let now = Date()
        let ops = [
            makeBpmSummary(systolic: 120, diastolic: 80, pulse: 70, date: now),
            makeBpmSummary(systolic: 125, diastolic: 82, pulse: 72, date: now.addingTimeInterval(-86400))
        ]

        let result = sut.buildBpmChartSeries(from: ops, period: .week)

        let names = Set(result.map(\.series))
        #expect(names.contains("systolic"))
        #expect(names.contains("diastolic"))
        #expect(names.contains("pulse"))
    }

    @Test("buildBpmChartSeries systolic values match input")
    func bpmChartSeriesSystolicValues() {
        let sut = makePreparer()
        let date = Date()
        let ops = [makeBpmSummary(systolic: 130, diastolic: 85, pulse: 68, date: date)]

        let result = sut.buildBpmChartSeries(from: ops, period: .week)
        let systolicPoints = result.filter { $0.series == "systolic" }

        #expect(!systolicPoints.isEmpty)
        #expect(systolicPoints.allSatisfy { $0.value == 130 })
    }

    @Test("buildBpmChartSeries diastolic values match input")
    func bpmChartSeriesDiastolicValues() {
        let sut = makePreparer()
        let date = Date()
        let ops = [makeBpmSummary(systolic: 130, diastolic: 85, pulse: 68, date: date)]

        let result = sut.buildBpmChartSeries(from: ops, period: .week)
        let diastolicPoints = result.filter { $0.series == "diastolic" }

        #expect(!diastolicPoints.isEmpty)
        #expect(diastolicPoints.allSatisfy { $0.value == 85 })
    }

    @Test("buildBpmChartSeries pulse values match input")
    func bpmChartSeriesPulseValues() {
        let sut = makePreparer()
        let date = Date()
        let ops = [makeBpmSummary(systolic: 120, diastolic: 80, pulse: 68, date: date)]

        let result = sut.buildBpmChartSeries(from: ops, period: .week)
        let pulsePoints = result.filter { $0.series == "pulse" }

        #expect(!pulsePoints.isEmpty)
        #expect(pulsePoints.allSatisfy { $0.value == 68 })
    }

    @Test("buildBpmChartSeries for year aggregates to monthly averages")
    func bpmChartSeriesYearAggregates() {
        let sut = makePreparer()
        let cal = Calendar.current
        let base = cal.startOfDay(for: Date())

        // Multiple readings in same month
        let ops = (0..<10).compactMap { offset -> BathScaleWeightSummary? in
            guard let date = cal.date(byAdding: .day, value: offset, to: base) else { return nil }
            return makeBpmSummary(systolic: 120, diastolic: 80, pulse: 70, date: date)
        }

        let week = sut.buildBpmChartSeries(from: ops, period: .week)
        let year = sut.buildBpmChartSeries(from: ops, period: .year)

        // Year series should have fewer or equal points (aggregated by month)
        let weekSystolic = week.filter { $0.series == "systolic" }.count
        let yearSystolic = year.filter { $0.series == "systolic" }.count
        #expect(yearSystolic <= weekSystolic)
    }

    // MARK: - buildBabyWeightSeries

    @Test("buildBabyWeightSeries returns empty for no operations")
    func babyWeightSeriesEmptyForNoOps() {
        let sut = makePreparer()
        let result = sut.buildBabyWeightSeries(from: []) { ConversionTools.convertStoredToLbs($0) }
        #expect(result.isEmpty)
    }

    @Test("buildBabyWeightSeries returns weight series points")
    func babyWeightSeriesReturnsWeightPoints() {
        let sut = makePreparer()
        let summaries = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", weight: 800),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", weight: 810)
        ]
        let result = sut.buildBabyWeightSeries(
            from: summaries
        ) { ConversionTools.convertStoredToLbs($0) }
        #expect(result.count == 2)
    }

    @Test("buildBabyWeightSeries converts weight correctly")
    func babyWeightSeriesConvertsWeightToDisplay() {
        let sut = makePreparer()
        let stored = 800 // 80.0 lbs
        let summary = DashboardTestFixtures.makeSummary(weight: Double(stored))

        let result = sut.buildBabyWeightSeries(
            from: [summary]
        ) { ConversionTools.convertStoredToLbs($0) }

        let expected = ConversionTools.convertStoredToLbs(stored)
        #expect(result.count == 1)
        #expect(abs(result[0].value - expected) < 0.001)
    }
}
