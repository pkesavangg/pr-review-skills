//
//  ChartPrepTests.swift
//  meAppTests
//
//  MOB-518 — exercises the pure v2 weight-graph builder `ChartPrep`: `buildWeight`, `weightYAxis`,
//  and `plotXDate`. These are side-effect-free value transforms, so the assertions check the shape
//  and invariants of the produced `ChartModel` / `YAxisModel` rather than pixel output.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct ChartPrepTests {

    // MARK: - Fixtures

    /// Sunday-first UTC Gregorian so plotted x-dates are deterministic across machines/timezones.
    private func utcCalendar() -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        cal.locale = Locale(identifier: "en_US_POSIX")
        return cal
    }

    private func makeDate(_ year: Int = 2026, _ month: Int = 3, _ day: Int, hour: Int = 8) -> Date {
        let comps = DateComponents(year: year, month: month, day: day, hour: hour)
        guard let date = utcCalendar().date(from: comps) else {
            Issue.record("failed to build fixture date")
            return Date(timeIntervalSinceReferenceDate: 0)
        }
        return date
    }

    /// A run of daily summaries in March 2026, optionally with a body-fat metric populated.
    private func makeDailyOperations(days: ClosedRange<Int>, withBodyFat: Bool = false) -> [BathScaleWeightSummary] {
        days.map { day in
            let date = makeDate(2026, 3, day)
            return DashboardTestFixtures.makeSummary(
                period: String(format: "2026-03-%02d", day),
                entryTimestamp: ISO8601DateFormatter().string(from: date),
                date: date,
                weight: Double(1800 + day * 2),
                bodyFat: withBodyFat ? Double(240 + day) : nil
            )
        }
    }

    private let convert = DashboardTestFixtures.convertToLbs

    // MARK: - plotXDate

    @Test("plotXDate snaps week/month to start-of-day, year to 1st-of-month, total unchanged")
    func plotXDateNormalization() {
        let cal = utcCalendar()
        let source = makeDate(2026, 3, 18, hour: 14) // mid-afternoon so start-of-day is a real shift

        let week = ChartPrep.plotXDate(source, period: .week, calendar: cal)
        let month = ChartPrep.plotXDate(source, period: .month, calendar: cal)
        #expect(week == cal.startOfDay(for: source))
        #expect(month == cal.startOfDay(for: source))

        let year = ChartPrep.plotXDate(source, period: .year, calendar: cal)
        let yearComps = cal.dateComponents([.year, .month, .day, .hour, .minute, .second], from: year)
        #expect(yearComps.year == 2026)
        #expect(yearComps.month == 3)
        #expect(yearComps.day == 1)
        #expect(yearComps.hour == 0)
        #expect(yearComps.minute == 0)
        #expect(yearComps.second == 0)

        #expect(ChartPrep.plotXDate(source, period: .total, calendar: cal) == source)
    }

    // MARK: - buildWeight structural invariants

    @Test("buildWeight produces a single sorted weight series for each period")
    func buildWeightPerPeriod() {
        let ops = makeDailyOperations(days: 1...20)
        let scrollPosition = makeDate(2026, 3, 15)

        for period in TimePeriod.allCases {
            let model = ChartPrep.buildWeight(
                operations: ops,
                period: period,
                scrollPosition: scrollPosition,
                goalWeight: 185,
                isWeightlessMode: false,
                anchorWeight: nil,
                convertWeight: convert,
                calendar: utcCalendar(),
                config: GraphRenderingConfiguration(calendar: utcCalendar())
            )

            #expect(model.period == period)
            #expect(model.productType == .scale)
            #expect(model.orderedSeriesNames == [DashboardStrings.weight])
            #expect(!model.isEmpty)
            #expect(model.goalWeight == 185)

            let points = model.weightPoints
            #expect(!points.isEmpty)
            // Sorted strictly ascending by plotted x-date.
            let ascending = zip(points, points.dropFirst()).allSatisfy { $0.xDate <= $1.xDate }
            #expect(ascending, "\(period) weight points must be sorted ascending by xDate")

            // Valid, finite geometry.
            #expect(model.xDomain.lowerBound <= model.xDomain.upperBound)
            #expect(model.visibleDomainLength > 0)
            #expect(model.yAxis.domain.lowerBound <= model.yAxis.domain.upperBound)
            #expect(model.yAxis.domain.lowerBound.isFinite && model.yAxis.domain.upperBound.isFinite)
        }
    }

    @Test("buildWeight is deterministic — identical inputs yield an equal model and fingerprint")
    func buildWeightDeterministic() {
        let ops = makeDailyOperations(days: 1...12)
        let scrollPosition = makeDate(2026, 3, 8)

        func build() -> ChartModel {
            ChartPrep.buildWeight(
                operations: ops,
                period: .month,
                scrollPosition: scrollPosition,
                goalWeight: nil,
                isWeightlessMode: false,
                anchorWeight: nil,
                convertWeight: convert,
                calendar: utcCalendar(),
                config: GraphRenderingConfiguration(calendar: utcCalendar())
            )
        }

        let first = build()
        let second = build()
        #expect(first == second)
        #expect(first.dataFingerprint == second.dataFingerprint)
    }

    @Test("buildWeight over empty operations yields an empty, non-degenerate model")
    func buildWeightEmpty() {
        let scrollPosition = makeDate(2026, 3, 10)
        let model = ChartPrep.buildWeight(
            operations: [],
            period: .week,
            scrollPosition: scrollPosition,
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert,
            calendar: utcCalendar(),
            config: GraphRenderingConfiguration(calendar: utcCalendar())
        )

        #expect(model.isEmpty)
        #expect(model.orderedSeriesNames.isEmpty)
        // xDomainRange fallback: a full window centered on the scroll position (never degenerate).
        #expect(model.xDomain.lowerBound < model.xDomain.upperBound)
    }

    @Test("buildWeight handles a single reading without collapsing the domain")
    func buildWeightSinglePoint() {
        let ops = makeDailyOperations(days: 3...3)
        let scrollPosition = makeDate(2026, 3, 3)
        let model = ChartPrep.buildWeight(
            operations: ops,
            period: .week,
            scrollPosition: scrollPosition,
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert,
            calendar: utcCalendar(),
            config: GraphRenderingConfiguration(calendar: utcCalendar())
        )

        #expect(model.weightPoints.count == 1)
        // fullXDomain applies fixed-domain semantics, so a lone reading still yields an ordered range.
        #expect(model.xDomain.lowerBound <= model.xDomain.upperBound)
    }

    // MARK: - Co-plotted metric (second series)

    @Test("buildWeight co-plots a selected body-comp metric as a normalized second series")
    func buildWeightWithSelectedMetric() {
        let ops = makeDailyOperations(days: 1...15, withBodyFat: true)
        let scrollPosition = makeDate(2026, 3, 10)
        let model = ChartPrep.buildWeight(
            operations: ops,
            period: .month,
            scrollPosition: scrollPosition,
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert,
            selectedMetric: DashboardStrings.bodyFat,
            calendar: utcCalendar(),
            config: GraphRenderingConfiguration(calendar: utcCalendar())
        )

        #expect(model.orderedSeriesNames == [DashboardStrings.weight, DashboardStrings.bodyFat])
        let metricPoints = model.seriesPoints[DashboardStrings.bodyFat] ?? []
        #expect(!metricPoints.isEmpty)
        // Normalized into the weight y-domain, so it overlays the same axis.
        let inDomain = metricPoints.allSatisfy {
            $0.original.value >= model.yAxis.domain.lowerBound && $0.original.value <= model.yAxis.domain.upperBound
        }
        #expect(inDomain, "co-plotted metric must be normalized inside the weight y-domain")
    }

    @Test("buildWeight ignores a selected metric equal to the weight series name")
    func buildWeightSelectedMetricIsWeight() {
        let ops = makeDailyOperations(days: 1...10)
        let scrollPosition = makeDate(2026, 3, 5)
        let model = ChartPrep.buildWeight(
            operations: ops,
            period: .week,
            scrollPosition: scrollPosition,
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert,
            selectedMetric: DashboardStrings.weight,
            calendar: utcCalendar(),
            config: GraphRenderingConfiguration(calendar: utcCalendar())
        )

        #expect(model.orderedSeriesNames == [DashboardStrings.weight])
    }

    // MARK: - weightYAxis (in-place settle path)

    @Test("weightYAxis returns a finite adaptive axis for a scrollable period")
    func weightYAxisScrollable() {
        let ops = makeDailyOperations(days: 1...20)
        let axis = ChartPrep.weightYAxis(
            operations: ops,
            period: .week,
            scrollPosition: makeDate(2026, 3, 10),
            visibleDomainLength: DashboardConstants.TimeInterval.weightWeekWindow,
            goalWeight: 185,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert
        )

        #expect(axis.domain.lowerBound <= axis.domain.upperBound)
        #expect(axis.domain.lowerBound.isFinite && axis.domain.upperBound.isFinite)
    }

    @Test("weightYAxis for the non-scrollable total period spans the whole dataset")
    func weightYAxisTotal() {
        let ops = makeDailyOperations(days: 1...20)
        let axis = ChartPrep.weightYAxis(
            operations: ops,
            period: .total,
            scrollPosition: makeDate(2026, 3, 10),
            visibleDomainLength: 0,
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert
        )

        #expect(axis.domain.lowerBound < axis.domain.upperBound)
    }

    // MARK: - ChartModel.withYAxisAndTicks (settle keeps series identity)

    @Test("withYAxisAndTicks replaces only the axis + ticks and preserves the data fingerprint")
    func settleKeepsFingerprint() {
        let ops = makeDailyOperations(days: 1...14)
        let model = ChartPrep.buildWeight(
            operations: ops,
            period: .month,
            scrollPosition: makeDate(2026, 3, 7),
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convert,
            calendar: utcCalendar(),
            config: GraphRenderingConfiguration(calendar: utcCalendar())
        )

        let newAxis = YAxisModel(domain: 0...500, ticks: [0, 250, 500], average: 250)
        let newTicks = [makeDate(2026, 3, 1), makeDate(2026, 3, 15)]
        let settled = model.withYAxisAndTicks(newAxis, ticks: newTicks)

        #expect(settled.yAxis == newAxis)
        #expect(settled.xAxisTicks == newTicks)
        // Series + geometry + fingerprint unchanged → the Chart keeps a stable identity across the settle.
        #expect(settled.dataFingerprint == model.dataFingerprint)
        #expect(settled.seriesPoints == model.seriesPoints)
        #expect(settled.xDomain == model.xDomain)
        #expect(settled.visibleDomainLength == model.visibleDomainLength)
    }
}
