//
//  TrendChartSelectionSnapperTests.swift
//  meAppTests
//
//  MOB-1515 (AC #2 + #3) — the per-period selection-snap logic extracted from the `TrendChartHost` View
//  into the pure `TrendChartSelectionSnapper`. Builds a real `ChartModel` via `ChartPrep.buildWeight` and
//  asserts a raw tap snaps to the period's grid — week→day, month→shown line/entry day, year→1st-of-month,
//  total→nearest entry — always clamped to the data range, plus the `nearestEntry` helper directly.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct TrendChartSelectionSnapperTests {

    private let seriesName = DashboardStrings.weight

    private func utcCalendar() -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        cal.locale = Locale(identifier: "en_US_POSIX")
        return cal
    }

    private func makeDate(_ year: Int = 2026, _ month: Int = 3, _ day: Int, hour: Int = 8) -> Date {
        guard let date = utcCalendar().date(from: DateComponents(year: year, month: month, day: day, hour: hour)) else {
            Issue.record("failed to build fixture date")
            return Date(timeIntervalSinceReferenceDate: 0)
        }
        return date
    }

    private func summary(_ year: Int, _ month: Int, _ day: Int) -> BathScaleWeightSummary {
        DashboardTestFixtures.makeSummary(
            period: String(format: "%04d-%02d-%02d", year, month, day),
            entryTimestamp: ISO8601DateFormatter().string(from: makeDate(year, month, day)),
            date: makeDate(year, month, day),
            weight: Double(1800 + day)
        )
    }

    private func model(_ ops: [BathScaleWeightSummary], period: TimePeriod, scroll: Date) -> ChartModel {
        ChartPrep.buildWeight(
            operations: ops,
            period: period,
            scrollPosition: scroll,
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs,
            calendar: utcCalendar(),
            config: GraphRenderingConfiguration(calendar: utcCalendar())
        )
    }

    private func snap(_ raw: Date, _ chartModel: ChartModel) -> Date? {
        TrendChartSelectionSnapper.snappedDate(
            for: raw, in: chartModel, primarySeriesName: seriesName, calendar: utcCalendar()
        )
    }

    // MARK: - Per-period snapping

    @Test("week snaps a mid-day tap to the nearest day (midnight)")
    func weekSnapsToNearestDay() {
        let chart = model((1...7).map { summary(2026, 3, $0) }, period: .week, scroll: makeDate(2026, 3, 4))
        // 03:00 rounds down to the same day's midnight.
        #expect(snap(makeDate(2026, 3, 4, hour: 3), chart) == utcCalendar().startOfDay(for: makeDate(2026, 3, 4)))
    }

    @Test("week clamps a tap before the first / after the last reading to the endpoints")
    func weekClampsOutOfRange() {
        let chart = model((1...7).map { summary(2026, 3, $0) }, period: .week, scroll: makeDate(2026, 3, 4))
        let cal = utcCalendar()
        #expect(snap(makeDate(2026, 2, 1), chart) == cal.startOfDay(for: makeDate(2026, 3, 1)))
        #expect(snap(makeDate(2026, 4, 1), chart) == cal.startOfDay(for: makeDate(2026, 3, 7)))
    }

    @Test("month snaps a tap on an entry day to that day's midnight (a shown candidate)")
    func monthSnapsToEntryDay() {
        let ops = [summary(2026, 3, 3), summary(2026, 3, 12), summary(2026, 3, 21)]
        let chart = model(ops, period: .month, scroll: makeDate(2026, 3, 12))
        #expect(snap(makeDate(2026, 3, 12, hour: 15), chart) == utcCalendar().startOfDay(for: makeDate(2026, 3, 12)))
    }

    @Test("year snaps to the nearest 1st-of-month, clamped to the data's month range")
    func yearSnapsToMonthStart() throws {
        let ops = [summary(2026, 1, 1), summary(2026, 2, 1), summary(2026, 3, 1), summary(2026, 4, 1)]
        let chart = model(ops, period: .year, scroll: makeDate(2026, 2, 1))
        // Mar 10 is 9 days past Mar 1 and 22 before Apr 1 → snaps to Mar 1.
        let expected = try #require(utcCalendar().date(from: DateComponents(year: 2026, month: 3, day: 1)))
        #expect(snap(makeDate(2026, 3, 10), chart) == expected)
    }

    @Test("total snaps to the nearest real entry date")
    func totalSnapsToNearestEntry() {
        let ops = [summary(2026, 3, 1), summary(2026, 3, 10), summary(2026, 3, 20)]
        let chart = model(ops, period: .total, scroll: makeDate(2026, 3, 1))
        #expect(snap(makeDate(2026, 3, 11), chart) == makeDate(2026, 3, 10)) // nearest entry
    }

    // MARK: - nearestEntry helper

    @Test("nearestEntry returns the exact entry on a direct hit and nil for an empty series")
    func nearestEntryHitAndEmpty() {
        let ops = [summary(2026, 3, 1), summary(2026, 3, 10), summary(2026, 3, 20)]
        let chart = model(ops, period: .total, scroll: makeDate(2026, 3, 1))
        let hit = TrendChartSelectionSnapper.nearestEntry(to: makeDate(2026, 3, 10), in: chart, primarySeriesName: seriesName)
        #expect(hit?.original.date == makeDate(2026, 3, 10))

        let empty = model([], period: .total, scroll: makeDate(2026, 3, 1))
        let miss = TrendChartSelectionSnapper.nearestEntry(to: makeDate(2026, 3, 10), in: empty, primarySeriesName: seriesName)
        #expect(miss == nil)
    }

    @Test("snappedDate returns nil when the model carries no readings")
    func snapNilOnEmptyModel() {
        let empty = model([], period: .week, scroll: makeDate(2026, 3, 1))
        #expect(snap(makeDate(2026, 3, 4), empty) == nil)
    }
}
