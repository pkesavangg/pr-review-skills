import Foundation
@testable import meApp
import Testing

extension GraphDataPreparerTests {

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

        #expect(sut.binarySearchFirst(in: []) { _ in true } == nil)
        #expect(sut.binarySearchFirst(in: ops) { $0.date >= makeDate(2026, 3, 3) } == 1)
        #expect(sut.binarySearchFirst(in: ops) { $0.date >= makeDate(2026, 3, 4) } == 2)
        #expect(sut.binarySearchFirst(in: ops) { $0.date >= makeDate(2026, 3, 6) } == nil)
    }

    @Test("binarySearchLast: handles empty, exact, in-between, and missing matches")
    func binarySearchLastCases() {
        let ops = [makeSummary(day: 1), makeSummary(day: 3), makeSummary(day: 5)]
        let sut = makeSUT()

        #expect(sut.binarySearchLast(in: []) { _ in true } == nil)
        #expect(sut.binarySearchLast(in: ops) { $0.date <= makeDate(2026, 3, 5) } == 2)
        #expect(sut.binarySearchLast(in: ops) { $0.date <= makeDate(2026, 3, 4) } == 1)
        #expect(sut.binarySearchLast(in: ops) { $0.date <= makeDate(2026, 2, 28) } == nil)
    }
}
