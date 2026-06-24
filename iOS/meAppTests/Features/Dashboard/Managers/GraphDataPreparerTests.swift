import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct GraphDataPreparerTests {

    private func makeSUT() -> GraphDataPreparer {
        GraphDataPreparer()
    }

    private func makeDate(_ year: Int = 2026, _ month: Int = 3, _ day: Int, hour: Int = 0) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
    }

    private func makeSummary(
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

    private func makeHourlyOperations(count: Int, startingAt start: Date = Date(timeIntervalSinceReferenceDate: 0)) -> [BathScaleWeightSummary] {
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

    private let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private func expectApprox(_ actual: Double?, _ expected: Double, tolerance: Double = 0.001) {
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

    @Test("findClosestPoint: returns the nearest summary by date")
    func findClosestPointNearest() {
        let ops = [
            makeSummary(day: 1),
            makeSummary(day: 5),
            makeSummary(day: 9)
        ]

        let result = makeSUT().findClosestPoint(to: makeDate(2026, 3, 6), in: ops)

        #expect(result?.date == makeDate(2026, 3, 5))
    }

    @Test("metricValue: maps all supported labels and scaling rules")
    func metricValueMappings() {
        let summary = makeSummary(
            day: 1,
            bodyFat: 25,
            muscleMass: 80,
            water: 55,
            bmi: 23,
            bmr: 16000,
            metabolicAge: 35,
            proteinPercent: 19,
            pulse: 72,
            skeletalMusclePercent: 41,
            subcutaneousFatPercent: 18,
            visceralFatLevel: 110,
            boneMass: 4,
        )
        let sut = makeSUT()

        #expect(sut.metricValue(for: DashboardStrings.bmi, from: summary) == 23)
        #expect(sut.metricValue(for: DashboardStrings.bodyFat, from: summary) == 25)
        #expect(sut.metricValue(for: DashboardStrings.muscle, from: summary) == 80)
        #expect(sut.metricValue(for: DashboardStrings.water, from: summary) == 55)
        #expect(sut.metricValue(for: DashboardStrings.heartBpm, from: summary) == 72)
        #expect(sut.metricValue(for: DashboardStrings.bone, from: summary) == 4)
        #expect(sut.metricValue(for: DashboardStrings.visceralFat, from: summary) == 11)
        #expect(sut.metricValue(for: DashboardStrings.subFat, from: summary) == 18)
        #expect(sut.metricValue(for: DashboardStrings.protein, from: summary) == 19)
        #expect(sut.metricValue(for: DashboardStrings.skelMuscle, from: summary) == 41)
        #expect(sut.metricValue(for: DashboardStrings.bmrKcal, from: summary) == 1600)
        #expect(sut.metricValue(for: DashboardStrings.metAge, from: summary) == 35)
        #expect(sut.metricValue(for: "unknown", from: summary) == nil)
    }

    @Test("staticMetricRange: returns the expected configured ranges")
    func staticMetricRangeMappings() {
        let sut = makeSUT()

        #expect(sut.staticMetricRange(for: DashboardStrings.bmi).min == 18.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.bmi).max == 35.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.heartBpm).min == 40.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.heartBpm).max == 200.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.visceralFat).min == 1.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.visceralFat).max == 30.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.bmrKcal).min == 1000.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.bmrKcal).max == 3000.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.metAge).min == 15.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.metAge).max == 80.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.bodyFat).min == 0.0)
        #expect(sut.staticMetricRange(for: DashboardStrings.bodyFat).max == 100.0)
    }

    @Test("canDisplay: requires at least two distinct metric values")
    func canDisplayBehavior() {
        let sut = makeSUT()
        let single = [makeSummary(day: 1, bmi: 23)]
        let flat = [makeSummary(day: 1, bmi: 23), makeSummary(day: 2, bmi: 23)]
        let variable = [makeSummary(day: 1, bmi: 23), makeSummary(day: 2, bmi: 24)]

        #expect(sut.canDisplay(DashboardStrings.bmi, in: single) == false)
        #expect(sut.canDisplay(DashboardStrings.bmi, in: flat) == false)
        #expect(sut.canDisplay(DashboardStrings.bmi, in: variable) == true)
    }

    @Test("availableMetrics: returns only metrics with enough variation to plot")
    func availableMetricsFiltersUnavailableMetrics() {
        let ops = [
            makeSummary(day: 1, bodyFat: 25, bmi: 23, pulse: 70),
            makeSummary(day: 2, bodyFat: 25, bmi: 24, pulse: 75)
        ]

        let result = makeSUT().availableMetrics(in: ops)

        #expect(result.contains(DashboardStrings.bmi))
        #expect(result.contains(DashboardStrings.heartBpm))
        #expect(result.contains(DashboardStrings.bodyFat) == false)
    }

    @Test("weightlessDisplay: returns nil without an anchor")
    func weightlessDisplayRequiresAnchor() {
        let result = makeSUT().weightlessDisplay(
            for: [makeSummary(day: 1, weight: 1800)],
            anchorWeight: nil,
            period: .week,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result == nil)
    }

    @Test("weightlessDisplay: week uses latest visible weight while total uses the average")
    func weightlessDisplayPeriodBehavior() {
        let ops = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 2, weight: 1820),
            makeSummary(day: 3, weight: 1840)
        ]
        let sut = makeSUT()

        let week = sut.weightlessDisplay(
            for: ops,
            anchorWeight: 180.0,
            period: .week,
            convertWeight: DashboardTestFixtures.convertToLbs
        )
        let total = sut.weightlessDisplay(
            for: ops,
            anchorWeight: 180.0,
            period: .total,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(week == 4.0)
        #expect(total == 2.0)
    }

    @Test("averageWeight: empty input is zero and weightless mode without anchor subtracts zero")
    func averageWeightEdgeBehavior() {
        let sut = makeSUT()
        let ops = [makeSummary(day: 1, weight: 1800), makeSummary(day: 2, weight: 1820)]

        #expect(sut.averageWeight(
            for: [],
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        ) == 0)

        #expect(sut.averageWeight(
            for: ops,
            isWeightlessMode: true,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        ) == 181.0)
    }

    @Test("averageWeight: rounds to two decimals")
    func averageWeightRoundsToTwoDecimals() {
        let ops = [
            makeSummary(day: 1, weight: 1801),
            makeSummary(day: 2, weight: 1802)
        ]

        let result = makeSUT().averageWeight(
            for: ops,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(result == 180.15)
    }

    @Test("interpolatedAverageForVisibleRange: invalid top-level inputs return nil")
    func interpolatedAverageInvalidInputs() {
        let sut = makeSUT()
        let sampleDate = makeDate(2026, 3, 2)

        #expect(sut.interpolatedAverageForVisibleRange(
            from: [makeSummary(day: 1, weight: 1800)],
            period: .total,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            labelRange: nil,
            sampleDates: [sampleDate]
        ) == nil)

        #expect(sut.interpolatedAverageForVisibleRange(
            from: [],
            period: .week,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            labelRange: nil,
            sampleDates: [sampleDate]
        ) == nil)

        #expect(sut.interpolatedAverageForVisibleRange(
            from: [makeSummary(day: 1, weight: 1800)],
            period: .week,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            labelRange: nil,
            sampleDates: []
        ) == nil)
    }

    @Test("interpolatedAverageForVisibleRange: filters to valid samples and averages their interpolated values")
    func interpolatedAverageForVisibleRangeFiltersSamples() {
        let ops = [
            makeSummary(day: 1, weight: 1800),
            makeSummary(day: 3, weight: 1820),
            makeSummary(day: 5, weight: 1840)
        ]
        let range = DateInterval(start: makeDate(2026, 3, 2), end: makeDate(2026, 3, 4))
        let result = makeSUT().interpolatedAverageForVisibleRange(
            from: ops,
            period: .year,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            labelRange: range,
            sampleDates: [
                makeDate(2026, 2, 20),
                makeDate(2026, 3, 2),
                makeDate(2026, 3, 4),
                makeDate(2026, 3, 7)
            ]
        )

        #expect(result == 182.0)
    }

    @Test("interpolatedAverageForVisibleRange: returns nil when interpolation cannot produce any valid weights")
    func interpolatedAverageForVisibleRangeNoValidInterpolation() {
        let result = makeSUT().interpolatedAverageForVisibleRange(
            from: [makeSummary(day: 1, weight: 1800), makeSummary(day: 2, weight: 1810)],
            period: .week,
            isWeightlessMode: true,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            labelRange: nil,
            sampleDates: [makeDate(2026, 3, 1), makeDate(2026, 3, 2)]
        )

        #expect(result == nil)
    }

    @Test("windowedOperations: datasets below threshold return the original collection")
    func windowedOperationsSmallDataset() {
        let ops = Array(0..<10).map { makeSummary(day: $0 + 1, weight: Double(1800 + $0)) }

        let result = makeSUT().windowedOperations(
            from: ops,
            scrollPosition: makeDate(2026, 3, 3),
            period: .week,
            visibleDomainLength: DashboardConstants.TimeInterval.week
        )

        #expect(result == ops)
    }

    @Test("windowedOperations: far-away scroll positions fall back to the original collection")
    func windowedOperationsNoWindowHit() {
        let ops = makeHourlyOperations(count: 2000, startingAt: makeDate(2026, 1, 1))

        let result = makeSUT().windowedOperations(
            from: ops,
            scrollPosition: makeDate(2027, 1, 1),
            period: .week,
            visibleDomainLength: DashboardConstants.TimeInterval.week
        )

        #expect(result.count == ops.count)
    }

    @Test("windowedOperations: returns a reduced subset when savings are meaningful")
    func windowedOperationsReducesLargeDataset() {
        let ops = makeHourlyOperations(count: 2000, startingAt: makeDate(2026, 1, 1))

        let result = makeSUT().windowedOperations(
            from: ops,
            scrollPosition: makeDate(2026, 2, 15),
            period: .week,
            visibleDomainLength: DashboardConstants.TimeInterval.day
        )

        #expect(result.count < ops.count)
        #expect(result.count < 500)
    }

    @Test("windowedOperations: keeps the original collection when savings are too small")
    func windowedOperationsKeepsOriginalWhenSavingsLow() {
        let ops = makeHourlyOperations(count: 2000, startingAt: makeDate(2026, 1, 1))

        let result = makeSUT().windowedOperations(
            from: ops,
            scrollPosition: makeDate(2026, 1, 10),
            period: .week,
            visibleDomainLength: DashboardConstants.TimeInterval.total
        )

        #expect(result.count == ops.count)
    }

    @Test("visibleOperations: includes left and right edge buffers")
    func visibleOperationsIncludesBuffers() {
        let scroll = makeDate(2026, 3, 10)
        let ops = [
            DashboardTestFixtures.makeSummary(period: "2026-03-08", date: scroll.addingTimeInterval(-2 * 86400), weight: 1700),
            DashboardTestFixtures.makeSummary(period: "2026-03-09", date: scroll.addingTimeInterval(-12 * 3600), weight: 1750),
            DashboardTestFixtures.makeSummary(period: "2026-03-11", date: scroll.addingTimeInterval(24 * 3600 + 1800), weight: 1850),
            DashboardTestFixtures.makeSummary(period: "2026-03-11", date: scroll.addingTimeInterval(24 * 3600 + 7200), weight: 1900)
        ].sorted { $0.date < $1.date }

        let result = makeSUT().visibleOperations(
            from: ops,
            scrollPosition: scroll,
            visibleDomainLength: DashboardConstants.TimeInterval.day
        )

        #expect(result.count == 2)
        #expect(result.map(\.weight) == [1750, 1850])
    }

    @Test("visibleOperations: returns empty when no items overlap the buffered domain")
    func visibleOperationsEmpty() {
        let ops = [makeSummary(day: 1), makeSummary(day: 2)]

        let result = makeSUT().visibleOperations(
            from: ops,
            scrollPosition: makeDate(2026, 3, 10),
            visibleDomainLength: DashboardConstants.TimeInterval.day
        )

        #expect(result.isEmpty)
    }

    @Test("strictlyVisibleOperations: clamps the requested range to dataset bounds")
    func strictlyVisibleOperationsClampsToBounds() {
        let ops = [
            makeSummary(day: 10),
            makeSummary(day: 11),
            makeSummary(day: 12)
        ]

        let result = makeSUT().strictlyVisibleOperations(
            from: ops,
            scrollPosition: makeDate(2026, 3, 9),
            visibleDomainLength: 2 * DashboardConstants.TimeInterval.day
        )

        #expect(result.map(\.date) == [makeDate(2026, 3, 10), makeDate(2026, 3, 11)])
    }

    @Test("strictlyVisibleOperations: returns empty when the requested window is after all data")
    func strictlyVisibleOperationsNoOverlap() {
        let result = makeSUT().strictlyVisibleOperations(
            from: [makeSummary(day: 1), makeSummary(day: 2)],
            scrollPosition: makeDate(2026, 3, 10),
            visibleDomainLength: DashboardConstants.TimeInterval.day
        )

        #expect(result.isEmpty)
    }

    @Test("bracketingOperations: returns the nearest points before and after the visible window")
    func bracketingOperationsPrevAndNext() {
        let ops = [
            makeSummary(day: 1),
            makeSummary(day: 5),
            makeSummary(day: 10)
        ]

        let result = makeSUT().bracketingOperations(
            from: ops,
            scrollPosition: makeDate(2026, 3, 6),
            visibleDomainLength: 2 * DashboardConstants.TimeInterval.day
        )

        #expect(result.map(\.date) == [makeDate(2026, 3, 5), makeDate(2026, 3, 10)])
    }

    @Test("bracketingOperations: avoids duplicating the same point when both sides resolve to one summary")
    func bracketingOperationsDeduplicatesSinglePoint() {
        let single = makeSummary(day: 5)

        let result = makeSUT().bracketingOperations(
            from: [single],
            scrollPosition: makeDate(2026, 3, 5),
            visibleDomainLength: 0
        )

        #expect(result.count == 1)
        #expect(result.first == single)
    }

    @Test("bracketingOperations: empty input returns no points")
    func bracketingOperationsEmpty() {
        let result = makeSUT().bracketingOperations(
            from: [],
            scrollPosition: makeDate(2026, 3, 5),
            visibleDomainLength: DashboardConstants.TimeInterval.day
        )

        #expect(result.isEmpty)
    }

    @Test("binarySearchFirst: handles empty, exact, in-between, and missing matches")
    func binarySearchFirstCases() {
        let ops = [makeSummary(day: 1), makeSummary(day: 3), makeSummary(day: 5)]
        let sut = makeSUT()

        #expect(sut.binarySearchFirst(in: [], where: { _ in true }) == nil)
        #expect(sut.binarySearchFirst(in: ops, where: { $0.date >= makeDate(2026, 3, 3) }) == 1)
        #expect(sut.binarySearchFirst(in: ops, where: { $0.date >= makeDate(2026, 3, 4) }) == 2)
        #expect(sut.binarySearchFirst(in: ops, where: { $0.date >= makeDate(2026, 3, 6) }) == nil)
    }

    @Test("binarySearchLast: handles empty, exact, in-between, and missing matches")
    func binarySearchLastCases() {
        let ops = [makeSummary(day: 1), makeSummary(day: 3), makeSummary(day: 5)]
        let sut = makeSUT()

        #expect(sut.binarySearchLast(in: [], where: { _ in true }) == nil)
        #expect(sut.binarySearchLast(in: ops, where: { $0.date <= makeDate(2026, 3, 5) }) == 2)
        #expect(sut.binarySearchLast(in: ops, where: { $0.date <= makeDate(2026, 3, 4) }) == 1)
        #expect(sut.binarySearchLast(in: ops, where: { $0.date <= makeDate(2026, 2, 28) }) == nil)
    }
}
