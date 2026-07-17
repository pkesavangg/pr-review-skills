import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct GraphRenderingConfigurationTests {
    private func makeSUT(now: Date? = nil) -> GraphRenderingConfiguration {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
        calendar.locale = Locale(identifier: "en_US_POSIX")
        let resolvedNow = now ?? DateTimeTools.getDateFromDateString("2026-03-15", format: "yyyy-MM-dd")
        return GraphRenderingConfiguration(calendar: calendar) { resolvedNow }
    }

    @Test("visible domain length and sample dates: match the selected period")
    func visibleDomainAndSampleDates() {
        let sut = makeSUT()
        let week = sut.sampleDates(for: .week, scrollPosition: date("2026-03-01"))
        let month = sut.sampleDates(for: .month, scrollPosition: date("2026-03-01"))
        let year = sut.sampleDates(for: .year, scrollPosition: date("2026-01-01"))

        #expect(sut.visibleDomainLength(for: .week) == DateTimeTools.visibleDomainLength(for: .week))
        #expect(sut.visibleDomainLength(for: .month, at: date("2026-02-10")) == 28 * DashboardConstants.TimeInterval.day)
        #expect(sut.visibleDomainLength(for: .month, at: date("2026-03-10")) == 31 * DashboardConstants.TimeInterval.day)
        #expect(week.count == 8)
        #expect(month.count >= 4)
        #expect(year.count >= 12)
        #expect(sut.sampleDates(for: .total, scrollPosition: date("2026-01-01")).isEmpty)
    }

    @Test("xAxisValues: empty input fixed-domain and rolling-range paths stay period aware")
    func xAxisValuesCoveragePaths() {
        let sut = makeSUT()
        let empty: [BathScaleWeightSummary] = []
        let shortYearOps = [
            DashboardTestFixtures.makeSummary(date: date("2026-01-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-12-01"))
        ]
        let longYearOps = [
            DashboardTestFixtures.makeSummary(date: date("2018-01-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-12-01"))
        ]
        let totalOps = [
            DashboardTestFixtures.makeSummary(date: date("2024-01-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-12-01"))
        ]

        #expect(sut.xAxisValues(for: .week, from: empty, scrollPosition: date("2026-03-01")).isEmpty)
        #expect(sut.xAxisValues(for: .year, from: shortYearOps, scrollPosition: date("2026-06-01")).count >= 13)
        #expect(sut.xAxisValues(for: .year, from: longYearOps, scrollPosition: date("2026-06-01")).count >= 12)
        #expect(sut.xAxisValues(for: .total, from: totalOps, scrollPosition: date("2026-06-01")).count >= 4)
    }

    @Test("tick generation: weekly monthly yearly and total produce aligned tick ranges")
    func tickGenerationPerPeriod() {
        let sut = makeSUT()
        let weekTicks = sut.weeklyTicks(from: date("2026-03-01"), to: date("2026-03-07"))
        let monthTicks = sut.monthlyTicks(from: date("2026-03-01"), to: date("2026-03-31"))
        let yearTicks = sut.yearlyTicks(from: date("2026-01-01"), to: date("2026-12-31"))
        let totalSameEra = sut.totalTicks(
            from: date("2026-01-01"),
            to: date("2026-12-31"),
            operations: [
                DashboardTestFixtures.makeSummary(date: date("2026-01-01")),
                DashboardTestFixtures.makeSummary(date: date("2026-12-01"))
            ]
        )
        let totalMultiEra = sut.totalTicks(
            from: date("2024-01-01"),
            to: date("2026-12-31"),
            operations: [
                DashboardTestFixtures.makeSummary(date: date("2024-01-01")),
                DashboardTestFixtures.makeSummary(date: date("2026-12-01"))
            ]
        )

        #expect(weekTicks.count == 8)
        #expect(monthTicks.count >= 5)
        #expect(yearTicks.count == 13)
        #expect(totalSameEra.count == 13)
        #expect(totalMultiEra.count >= 4)
        #expect(weekTicks.first == isoDate("2026-03-01T12:00:00Z"))
        #expect(weekTicks.last == isoDate("2026-03-08T12:00:00Z"))
        #expect(yearTicks.first == isoDate("2026-01-01T12:00:00Z"))
        #expect(yearTicks.last == isoDate("2027-01-01T12:00:00Z"))
    }

    @Test("month ticks: start at day 1 and stay inside the calendar month")
    func monthTicksStartAtFirstOfMonth() {
        let sut = makeSUT()
        let ticks = sut.monthlyTicks(
            from: isoDate("2026-03-01T00:00:00Z"),
            to: isoDate("2026-03-31T00:00:00Z")
        )

        #expect(ticks == [
            isoDate("2026-03-01T12:00:00Z"),
            isoDate("2026-03-08T12:00:00Z"),
            isoDate("2026-03-15T12:00:00Z"),
            isoDate("2026-03-22T12:00:00Z"),
            isoDate("2026-03-29T12:00:00Z"),
            isoDate("2026-03-31T12:00:00Z")
        ])
    }

    @Test("xAxisValues: month view for March does not include next-month label ticks")
    func monthXAxisValuesStayWithinCalendarMonth() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(date: isoDate("2026-03-01T00:00:00Z")),
            DashboardTestFixtures.makeSummary(date: isoDate("2026-03-02T00:00:00Z")),
            DashboardTestFixtures.makeSummary(date: isoDate("2026-03-03T00:00:00Z"))
        ]
        let ticks = sut.xAxisValues(for: .month, from: ops, scrollPosition: isoDate("2026-03-01T00:00:00Z"))

        #expect(ticks == [
            isoDate("2026-03-01T12:00:00Z"),
            isoDate("2026-03-08T12:00:00Z"),
            isoDate("2026-03-15T12:00:00Z"),
            isoDate("2026-03-22T12:00:00Z"),
            isoDate("2026-03-29T12:00:00Z"),
            isoDate("2026-03-31T12:00:00Z")
        ])
    }

    @Test("xAxisValues: month view with older single entry extends through current month")
    func monthXAxisValuesForOlderSingleEntryExtendThroughCurrentMonth() {
        let sut = makeSUT(now: isoDate("2026-04-13T12:00:00Z"))
        let ops = [
            DashboardTestFixtures.makeSummary(date: isoDate("2026-01-07T00:00:00Z"))
        ]

        let ticks = sut.xAxisValues(for: .month, from: ops, scrollPosition: isoDate("2026-01-01T00:00:00Z"))

        #expect(ticks.first == isoDate("2026-01-01T12:00:00Z"))
        #expect(ticks.contains(isoDate("2026-04-01T12:00:00Z")))
        #expect(ticks.last == isoDate("2026-04-30T12:00:00Z"))
        #expect(!ticks.contains(isoDate("2026-05-01T12:00:00Z")))
    }

    @Test("optimal scroll position: total returns minimum and latest mode biases right edge")
    func optimalScrollPosition() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(date: date("2026-01-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-03-01"))
        ]

        #expect(sut.optimalScrollPosition(for: .total, from: ops) == date("2026-01-01"))
        #expect(sut.optimalScrollPosition(for: .week, from: ops, showingLatest: true) > date("2026-02-20"))
        #expect(sut.optimalScrollPosition(for: .month, from: ops, anchorDate: date("2026-02-15"), showingLatest: false) <= date("2026-02-15"))
    }

    @Test("snap and clamp scroll position: align to period boundaries and padded bounds")
    func snapAndClampScrollPosition() {
        let sut = makeSUT()
        let week = sut.snapScrollPosition(date("2026-03-04"), for: .week)
        let month = sut.snapScrollPosition(date("2026-03-04"), for: .month)
        let year = sut.snapScrollPosition(date("2026-03-16"), for: .year)
        let clamped = sut.clampScrollPosition(date("2026-05-01"), for: .week, minDate: date("2026-03-01"), maxDate: date("2026-03-10"))

        #expect(week == isoDate("2026-03-04T12:00:00Z"))
        #expect(month == isoDate("2026-03-01T00:00:00Z"))
        #expect(sut.snapScrollPosition(date("2026-03-20"), for: .month) == isoDate("2026-03-01T00:00:00Z"))
        #expect(year == isoDate("2026-03-01T12:00:00Z"))
        #expect(clamped == isoDate("2026-03-17T03:36:00Z"))
    }

    @Test("formatting helpers: selected dates ranges fallback labels and x-axis labels are period aware")
    func formattingHelpers() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(date: date("2026-03-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-03-03"))
        ]

        #expect(sut.formatSelectedDate(date("2026-03-10"), for: .week) == "Mar 10, 2026")
        #expect(sut.formatSelectedDate(date("2026-03-10"), for: .year) == "Mar 2026")
        #expect(sut.formatDateRange(minDate: date("2026-03-01"), maxDate: date("2026-03-07"), for: .week) == "mar 1 - mar 6, 2026")
        #expect(sut.formatDateRange(minDate: date("2026-03-01"), maxDate: date("2026-03-31"), for: .month) == "Mar 2026")
        #expect(sut.fallbackTimeLabel(for: .month).contains(" "))
        #expect(sut.formatXAxisLabel(for: date("2026-03-01"), period: .week, operations: ops) != nil)
    }

    @Test("formatting and scrolling: cover total ranges cached bounds and total clamping")
    func formattingAndScrollCoveragePaths() {
        let sut = makeSUT()
        let cachedBounds = (min: date("2026-01-01"), max: date("2026-03-10"))
        let anchored = sut.optimalScrollPosition(
            for: .month,
            from: [],
            anchorDate: date("2026-03-09"),
            showingLatest: false,
            cachedBounds: cachedBounds
        )
        let totalClamp = sut.clampScrollPosition(
            date("2026-05-01"),
            for: .total,
            minDate: date("2026-03-01"),
            maxDate: date("2026-03-10")
        )

        #expect(anchored <= cachedBounds.max)
        #expect(totalClamp == date("2026-03-10"))
        #expect(sut.snapScrollPosition(date("2026-03-04"), for: .total) == date("2026-03-04"))
        #expect(sut.formatDateRange(minDate: date("2026-03-01"), maxDate: date("2026-03-01"), for: .total) == "Mar 2026")
        #expect(sut.formatDateRange(minDate: date("2025-12-01"), maxDate: date("2026-03-01"), for: .month) == "Dec 1, 2025 – Mar 1, 2026")
        #expect(sut.formatDateRange(minDate: date("2026-01-01"), maxDate: date("2026-12-01"), for: .year) == "2026")
        #expect(sut.formatDateRange(minDate: date("2025-01-01"), maxDate: date("2026-12-01"), for: .year) == "Jan 2025 – Dec 2026")
        #expect(!sut.fallbackTimeLabel(for: .week).isEmpty)
        #expect(!sut.fallbackTimeLabel(for: .year).isEmpty)
        #expect(!sut.fallbackTimeLabel(for: .total).isEmpty)
    }

    // MARK: - MOB-1516 — single-reading domain insets (no flush-left point)

    @Test("fullXDomain week: a lone reading spans its full Sun→Sat containing week (inset, even columns)")
    func fullWeekDomainSingleReadingInset() {
        let sut = makeSUT()
        // 2026-03-19 is a Thursday. Before the fix the domain began exactly here (flush left) and ended at
        // Saturday-noon (squished last column).
        let thursday = isoDate("2026-03-19T00:00:00Z")
        let ops = [DashboardTestFixtures.makeSummary(date: thursday)]

        let domain = sut.fullXDomain(for: .week, from: ops)
        #expect(domain != nil)
        // Opens on the containing week's Sunday (2026-03-15) — strictly before the reading, so the point sits
        // at its weekday inset from the left edge.
        #expect(domain?.lowerBound == isoDate("2026-03-15T00:00:00Z"))
        // A full 7-day window that ends just before the next Sunday (2026-03-22) → even day columns with the
        // last column (Saturday) no longer flush against the right rule.
        #expect(domain.map { $0.upperBound < isoDate("2026-03-22T00:00:00Z") } == true)
        #expect(domain.map { $0.upperBound.timeIntervalSince($0.lowerBound) > 6 * DashboardConstants.TimeInterval.day } == true)
    }

    @Test("fullXDomain week: a mid-week first entry still opens on the containing week's Sunday")
    func fullWeekDomainMultiEntryBracketsWholeWeek() {
        // now is inside the week of 2026-03-15 (Sunday); FIRST entry is Wednesday, LAST is Friday of that week.
        let sut = makeSUT(now: date("2026-03-18"))
        let ops = [
            DashboardTestFixtures.makeSummary(date: date("2026-03-18")),
            DashboardTestFixtures.makeSummary(date: date("2026-03-20"))
        ]
        let domain = sut.fullXDomain(for: .week, from: ops)
        // Left edge = the Sunday of the first entry's week (2026-03-15), NOT Wednesday — so the whole Sun→Sat
        // week renders with the first point on its weekday instead of pinned to the left edge.
        #expect(domain?.lowerBound == isoDate("2026-03-15T00:00:00Z"))
        // Right edge = just before the next Sunday (2026-03-22 00:00, exclusive) so the last column is even.
        #expect(domain.map { $0.upperBound < isoDate("2026-03-22T00:00:00Z") } == true)
        #expect(domain.map { $0.upperBound.timeIntervalSince($0.lowerBound) > 6 * DashboardConstants.TimeInterval.day } == true)
    }

    @Test("fullXDomain week: extends forward to the current week when the last reading is in a past week")
    func fullWeekDomainExtendsToCurrentWeek() {
        // Reading in the week of 2026-06-15 (Mon; that week is Sun 2026-06-14 → Sat 2026-06-20); "now" is a
        // month later, in the week Sun 2026-07-12 → Sat 2026-07-18.
        let sut = makeSUT(now: isoDate("2026-07-16T12:00:00Z"))
        let ops = [DashboardTestFixtures.makeSummary(date: isoDate("2026-06-15T00:00:00Z"))]

        let domain = sut.fullXDomain(for: .week, from: ops)
        // Opens on the reading's week Sunday…
        #expect(domain?.lowerBound == isoDate("2026-06-14T00:00:00Z"))
        // …and runs forward to the CURRENT week's end (just before next Sunday 2026-07-19), so today's week is
        // reachable by scrolling — not stopping at the June reading's week.
        #expect(domain.map { $0.upperBound < isoDate("2026-07-19T00:00:00Z") } == true)
        #expect(domain.map { $0.upperBound > isoDate("2026-07-12T00:00:00Z") } == true)
    }

    @Test("fullXDomain year: extends forward to the current year when the last reading is in a past year")
    func fullYearDomainExtendsToCurrentYear() {
        let sut = makeSUT(now: isoDate("2026-07-16T12:00:00Z"))
        let ops = [DashboardTestFixtures.makeSummary(date: isoDate("2025-04-10T00:00:00Z"))]

        let domain = sut.fullXDomain(for: .year, from: ops)
        #expect(domain?.lowerBound == isoDate("2025-01-01T00:00:00Z"))
        // Runs through the end of the CURRENT year (2026), not just the reading's year (2025).
        #expect(domain.map { $0.upperBound >= isoDate("2026-12-31T00:00:00Z") } == true)
        #expect(domain.map { $0.upperBound < isoDate("2027-01-01T00:00:00Z") } == true)
    }

    @Test("fullXDomain empty state: no readings still yields the current period's domain + ticks (blank total)")
    func fullDomainEmptyStateRendersCurrentPeriod() {
        // now = 2026-07-16 (a Thursday in the week Sun 2026-07-12 → Sat 2026-07-18).
        let sut = makeSUT(now: isoDate("2026-07-16T12:00:00Z"))
        let empty: [BathScaleWeightSummary] = []
        let around = isoDate("2026-07-16T12:00:00Z")

        // WEEK → the current Sun→Sat week, with day gridlines/labels (no longer a blank box).
        let week = sut.fullXDomain(for: .week, from: empty)
        #expect(week?.lowerBound == isoDate("2026-07-12T00:00:00Z"))
        #expect(week.map { $0.upperBound < isoDate("2026-07-19T00:00:00Z") } == true)
        #expect(!sut.boundedXAxisValues(for: .week, from: empty, around: around, windows: 10).isEmpty)

        // MONTH → the current calendar month, with the weekly grid.
        let month = sut.fullXDomain(for: .month, from: empty)
        #expect(month?.lowerBound == isoDate("2026-07-01T00:00:00Z"))
        #expect(!sut.boundedXAxisValues(for: .month, from: empty, around: around, windows: 10).isEmpty)

        // YEAR → the current calendar year, with the twelve months.
        let year = sut.fullXDomain(for: .year, from: empty)
        #expect(year?.lowerBound == isoDate("2026-01-01T00:00:00Z"))
        #expect(!sut.boundedXAxisValues(for: .year, from: empty, around: around, windows: 10).isEmpty)

        // TOTAL → a domain exists (current year) but NO ticks are drawn — stays a bare box, as before.
        #expect(sut.fullXDomain(for: .total, from: empty) != nil)
        #expect(sut.boundedXAxisValues(for: .total, from: empty, around: around, windows: 10).isEmpty)
    }

    @Test("fullXDomain total: pads the data range 6 months on both ends and emits no x-axis ticks")
    func fullTotalDomainPadsBothEnds() {
        let sut = makeSUT()

        // Single reading → a 12-month window centred on it (point mid-plot, not flush left).
        let reading = date("2026-07-16")
        let singleOps = [DashboardTestFixtures.makeSummary(date: reading)]
        let single = sut.fullXDomain(for: .total, from: singleOps)
        #expect(single?.lowerBound == date("2026-01-16"))
        #expect(single?.upperBound == date("2027-01-16"))

        // Multiple readings → 6-month pad BEFORE the first and AFTER the last (equal breathing room, so the
        // first reading is no longer flush against the left edge). Mirrors the reported jul-2024/jul-2026 data.
        let multi = [
            DashboardTestFixtures.makeSummary(date: date("2024-07-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-07-01"))
        ]
        let domain = sut.fullXDomain(for: .total, from: multi)
        #expect(domain?.lowerBound == date("2024-01-01"))
        #expect(domain?.upperBound == date("2027-01-01"))

        // TOTAL never emits x-axis ticks (no labels, no gridlines), single reading OR many.
        #expect(sut.boundedXAxisValues(for: .total, from: singleOps, around: reading, windows: 10).isEmpty)
        #expect(sut.boundedXAxisValues(for: .total, from: multi, around: date("2026-07-01"), windows: 10).isEmpty)
    }

    // Parse in GMT (not the device timezone) so `date("2026-03-01")` is exactly 2026-03-01T00:00:00Z.
    // The SUT is built with a GMT calendar and assertions compare against UTC `isoDate(...)` values,
    // so building inputs in the device timezone (as DateTimeTools.getDateFromDateString does) would
    // shift every boundary on any CI machine whose timezone isn't UTC — the cause of the CI-only flakes.
    private func date(_ value: String) -> Date {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: value) else {
            Issue.record("unexpected nil date from \(value)")
            return Date()
        }
        return date
    }

    private func isoDate(_ value: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        guard let date = formatter.date(from: value) else {
            Issue.record("unexpected nil")
            return Date()
        }
        return date
    }
}
