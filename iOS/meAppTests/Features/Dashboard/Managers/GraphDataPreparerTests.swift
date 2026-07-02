import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct GraphDataPreparerTests {

    func makeSUT() -> GraphDataPreparer {
        GraphDataPreparer()
    }

    func makeDate(_ year: Int = 2026, _ month: Int = 3, _ day: Int, hour: Int = 0) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        guard let date = calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour)) else {
            Issue.record("unexpected nil")
            return Date()
        }
        return date
    }

    func makeSummary(
        day: Int,
        hour: Int = 0,
        weight: Double = 1800,
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
        boneMass: Double? = nil
    ) -> BathScaleWeightSummary {
        let date = makeDate(2026, 3, day, hour: hour)
        return DashboardTestFixtures.makeSummary(
            period: "2026-03-\(String(format: "%02d", day))",
            entryTimestamp: ISO8601DateFormatter().string(from: date),
            date: date,
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass
        )
    }

    func makeHourlyOperations(count: Int, startingAt start: Date = Date(timeIntervalSinceReferenceDate: 0)) -> [BathScaleWeightSummary] {
        (0..<count).map { index in
            let date = start.addingTimeInterval(Double(index) * 3600)
            return DashboardTestFixtures.makeSummary(
                period: dayFormatter.string(from: date),
                entryTimestamp: ISO8601DateFormatter().string(from: date),
                date: date,
                weight: Double(1800 + index)
            )
        }
    }

    let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    func expectApprox(_ actual: Double?, _ expected: Double, tolerance: Double = 0.001) {
        guard let actual else {
            Issue.record("Expected \(expected), got nil")
            return
        }
        #expect(abs(actual - expected) <= tolerance)
    }

    @Test("buildChartSeries: empty operations returns no series")
    func buildChartSeriesEmpty() {
        let result = makeSUT().buildChartSeries(
            from: [],
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 100...200,
            period: .week
        )

        #expect(result.isEmpty)
    }

    @Test("buildChartSeries: selected weight returns weight series only")
    func buildChartSeriesWeightOnly() {
        let ops = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 2, weight: 1820)
        ]

        let result = makeSUT().buildChartSeries(
            from: ops,
            selectedMetric: DashboardStrings.weight,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: nil,
            period: .week
        )

        #expect(result.count == 2)
        #expect(result.allSatisfy { $0.series == DashboardStrings.weight })
        #expect(result.map(\.value) == [180.0, 182.0])
    }

    @Test("buildChartSeries: single operation with nil selected metric returns only the weight series")
    func buildChartSeriesSingleOperationNilMetric() {
        let ops = [makeSummary(day: 1, weight: 1800)]

        let result = makeSUT().buildChartSeries(
            from: ops,
            selectedMetric: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: nil,
            period: .month
        )

        #expect(result.count == 1)
        #expect(result.first?.series == DashboardStrings.weight)
        #expect(result.first?.value == 180)
    }

    @Test("buildChartSeries: explicit domain uses axis operations and clamps outliers into safe bounds")
    func buildChartSeriesWithExplicitDomainClampsOutlier() {
        let ops = [
            makeSummary(day: 1, weight: 1800, bodyFat: 10),
            makeSummary(day: 2, weight: 1810, bodyFat: 50),
            makeSummary(day: 3, weight: 1820, bodyFat: 90)
        ]

        let result = makeSUT().buildChartSeries(
            from: ops,
            selectedMetric: DashboardStrings.bodyFat,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: 100...200,
            visibleOperations: ops,
            operationsForYAxis: Array(ops.prefix(2)),
            period: .week
        )

        let metricSeries = result.filter { $0.series == DashboardStrings.bodyFat }
        #expect(metricSeries.count == 3)
        expectApprox(metricSeries[0].value, 105.909, tolerance: 0.01)
        expectApprox(metricSeries[1].value, 194.091, tolerance: 0.01)
        expectApprox(metricSeries[2].value, 198.5)
    }

    @Test("buildChartSeries: flat weight range falls back to default normalization domain")
    func buildChartSeriesFlatWeightFallbackRange() {
        let ops = [
            makeSummary(day: 1, weight: 1800, bmi: 20),
            makeSummary(day: 2, weight: 1800, bmi: 24)
        ]

        let result = makeSUT().buildChartSeries(
            from: ops,
            selectedMetric: DashboardStrings.bmi,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            yAxisDomain: nil,
            period: .week
        )

        let metricSeries = result.filter { $0.series == DashboardStrings.bmi }
        #expect(metricSeries.count == 2)
        #expect(metricSeries.allSatisfy { $0.value >= 0 && $0.value <= 1 })
    }

    @Test("buildWeightSeries: converts weights in normal mode")
    func buildWeightSeriesNormalMode() {
        let ops = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 2, weight: 1815)
        ]

        let result = makeSUT().buildWeightSeries(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result.map(\.value) == [180.0, 181.5])
    }

    @Test("buildWeightSeries: preserves fractional stored average for kg display")
    func buildWeightSeriesPreservesFractionalStoredAverageForKgDisplay() {
        let ops = [makeSummary(day: 17, weight: 2452.0 / 6.0)]
        let convertToKg: (Double) -> Double = { stored in
            ((stored / 22.0462) * 10).rounded(.toNearestOrAwayFromZero) / 10
        }

        let result = makeSUT().buildWeightSeries(
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convertToKg
        )

        #expect(result.map(\.value) == [18.5])
    }

    @Test("buildWeightSeries: weightless mode subtracts anchor and drops points when anchor is missing")
    func buildWeightSeriesWeightlessBehavior() {
        let ops = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 2, weight: 1820)
        ]
        let sut = makeSUT()

        let withAnchor = sut.buildWeightSeries(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: 180.0,
            convertWeight: DashboardTestFixtures.convertToLbs
        )
        let withoutAnchor = sut.buildWeightSeries(
            from: ops,
            isWeightlessMode: true,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(withAnchor.map(\.value) == [0.0, 2.0])
        #expect(withoutAnchor.isEmpty)
    }

    @Test("buildNormalizedMetricSeries: returns empty when metric values are unavailable")
    func buildNormalizedMetricSeriesNoValues() {
        let result = makeSUT().buildNormalizedMetricSeries(
            for: DashboardStrings.bodyFat,
            from: [makeSummary(day: 1), makeSummary(day: 2)],
            toWeightRange: 100...200
        )

        #expect(result.isEmpty)
    }

    @Test("buildNormalizedMetricSeries: flat metrics use static fallback range")
    func buildNormalizedMetricSeriesFlatMetrics() {
        let ops = [
            makeSummary(day: 1, bodyFat: 25),
            makeSummary(day: 2, bodyFat: 25)
        ]

        let result = makeSUT().buildNormalizedMetricSeries(
            for: DashboardStrings.bodyFat,
            from: ops,
            toWeightRange: 100...200
        )

        #expect(result.count == 2)
        expectApprox(result[0].value, 125.0)
        expectApprox(result[1].value, 125.0)
    }

    @Test("buildNormalizedMetricSeries: padded dynamic range keeps values off the edges")
    func buildNormalizedMetricSeriesDynamicRange() {
        let ops = [
            makeSummary(day: 1, bmi: 20),
            makeSummary(day: 2, bmi: 30)
        ]

        let result = makeSUT().buildNormalizedMetricSeries(
            for: DashboardStrings.bmi,
            from: ops,
            toWeightRange: 100...200
        )

        expectApprox(result[0].value, 104.545, tolerance: 0.01)
        expectApprox(result[1].value, 195.455, tolerance: 0.01)
    }

    @Test("buildNormalizedMetricSeriesWithDomain: single-point range is placed at sixty percent of the domain")
    func buildNormalizedMetricSeriesWithDomainSinglePoint() {
        let ops = [
            makeSummary(day: 1, bmi: 23),
            makeSummary(day: 2, bmi: 23)
        ]

        let result = makeSUT().buildNormalizedMetricSeriesWithDomain(
            for: DashboardStrings.bmi,
            from: ops,
            visibleOperations: ops,
            operationsForYAxis: ops,
            toWeightDomain: 100...200,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result.count == 2)
        #expect(result.allSatisfy { abs($0.value - 160.0) <= 0.001 })
    }

    @Test("buildNormalizedMetricSeriesWithDomain: falls back to all operations when axis operations have no metric values")
    func buildNormalizedMetricSeriesWithDomainFallbacksToAllOperations() {
        let allOps = [
            makeSummary(day: 1, pulse: 60),
            makeSummary(day: 2, pulse: 80)
        ]
        let axisOps = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 2, weight: 1810)
        ]

        let result = makeSUT().buildNormalizedMetricSeriesWithDomain(
            for: DashboardStrings.heartBpm,
            from: allOps,
            visibleOperations: allOps,
            operationsForYAxis: axisOps,
            toWeightDomain: 100...200,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result.count == 2)
        #expect(result.allSatisfy { $0.value >= 101.5 && $0.value <= 198.5 })
    }

    @Test("interpolatedDisplayWeight: returns nil for empty input")
    func interpolatedDisplayWeightEmpty() {
        let result = makeSUT().interpolatedDisplayWeight(
            at: makeDate(2026, 3, 1),
            from: [],
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .week
        )

        #expect(result == nil)
    }

    @Test("interpolatedDisplayWeight: single point returns its display weight")
    func interpolatedDisplayWeightSinglePoint() {
        let result = makeSUT().interpolatedDisplayWeight(
            at: makeDate(2026, 3, 1),
            from: [makeSummary(day: 1, weight: 1800)],
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .week
        )

        #expect(result == 180.0)
    }

    @Test("interpolatedDisplayWeight: linear two-point midpoint stays accurate")
    func interpolatedDisplayWeightTwoPointMidpoint() {
        let ops = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 3, weight: 2000)
        ]

        let result = makeSUT().interpolatedDisplayWeight(
            at: makeDate(2026, 3, 2),
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .year
        )

        expectApprox(result, 190.0)
    }

    @Test("interpolatedDisplayWeight: week view normalizes times within a day")
    func interpolatedDisplayWeightWeekNormalizesSameDayTimes() {
        let ops = [
            makeSummary(day: 1, hour: 0, weight: 1800),
            makeSummary(day: 2, hour: 0, weight: 2000)
        ]
        let sut = makeSUT()

        let morning = sut.interpolatedDisplayWeight(
            at: makeDate(2026, 3, 1, hour: 6),
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .week
        )
        let evening = sut.interpolatedDisplayWeight(
            at: makeDate(2026, 3, 1, hour: 18),
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .week
        )

        #expect(morning == 180.0)
        #expect(evening == 180.0)
    }

    @Test("interpolatedDisplayWeight: weightless mode without anchor returns nil")
    func interpolatedDisplayWeightWeightlessWithoutAnchor() {
        let result = makeSUT().interpolatedDisplayWeight(
            at: makeDate(2026, 3, 2),
            from: [makeSummary(day: 1, weight: 1800), makeSummary(day: 2, weight: 1810)],
            isWeightlessMode: true,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .week
        )

        #expect(result == nil)
    }

    @Test("interpolatedDisplayWeight: values outside the data bounds clamp to edge values")
    func interpolatedDisplayWeightClampsOutsideBounds() {
        let ops = [
            makeSummary(day: 3, weight: 1800),
            makeSummary(day: 5, weight: 2000)
        ]
        let sut = makeSUT()

        let before = sut.interpolatedDisplayWeight(
            at: makeDate(2026, 3, 1),
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .year
        )
        let after = sut.interpolatedDisplayWeight(
            at: makeDate(2026, 3, 10),
            from: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            period: .year
        )

        #expect(before == 180.0)
        #expect(after == 200.0)
    }
}
