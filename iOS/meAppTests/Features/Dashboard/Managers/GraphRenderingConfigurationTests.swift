import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct GraphRenderingConfigurationTests {
    private func makeSUT() -> GraphRenderingConfiguration {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return GraphRenderingConfiguration(calendar: calendar)
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
            ],
            shouldRepeat: false
        )
        let totalMultiEra = sut.totalTicks(
            from: date("2024-01-01"),
            to: date("2026-12-31"),
            operations: [
                DashboardTestFixtures.makeSummary(date: date("2024-01-01")),
                DashboardTestFixtures.makeSummary(date: date("2026-12-01"))
            ],
            shouldRepeat: false
        )

        #expect(weekTicks.count == 15)
        #expect(monthTicks.count >= 5)
        #expect(yearTicks.count == 25)
        #expect(totalSameEra.count == 5)
        #expect(totalMultiEra.count >= 4)
        #expect(weekTicks.first == isoDate("2026-02-22T12:00:00Z"))
        #expect(weekTicks.last == isoDate("2026-03-08T12:00:00Z"))
        #expect(yearTicks.first == isoDate("2025-01-01T12:00:00Z"))
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

        #expect(week == isoDate("2026-03-03T12:00:00Z"))
        #expect(month == isoDate("2026-03-01T00:00:00Z"))
        #expect(sut.snapScrollPosition(date("2026-03-20"), for: .month) == isoDate("2026-03-01T00:00:00Z"))
        #expect(year == isoDate("2026-03-01T12:00:00Z"))
        #expect(clamped == isoDate("2026-03-16T22:06:00Z"))
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
        #expect(sut.formatDateRange(minDate: date("2026-03-01"), maxDate: date("2026-03-07"), for: .week) == "feb 28 - mar 5, 2026")
        #expect(sut.formatDateRange(minDate: date("2026-03-01"), maxDate: date("2026-03-31"), for: .month) == "Feb 28 – Mar 30, 2026")
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
        #expect(sut.formatDateRange(minDate: date("2026-03-01"), maxDate: date("2026-03-01"), for: .total) == "Feb 2026")
        #expect(sut.formatDateRange(minDate: date("2025-12-01"), maxDate: date("2026-03-01"), for: .month) == "Nov 30, 2025 – Feb 28, 2026")
        #expect(sut.formatDateRange(minDate: date("2026-01-01"), maxDate: date("2026-12-01"), for: .year) == "Dec 2025 – Nov 2026")
        #expect(sut.formatDateRange(minDate: date("2025-01-01"), maxDate: date("2026-12-01"), for: .year) == "Dec 2024 – Nov 2026")
        #expect(!sut.fallbackTimeLabel(for: .week).isEmpty)
        #expect(!sut.fallbackTimeLabel(for: .year).isEmpty)
        #expect(!sut.fallbackTimeLabel(for: .total).isEmpty)
    }

    private func date(_ value: String) -> Date {
        DateTimeTools.getDateFromDateString(value, format: "yyyy-MM-dd")
    }

    private func isoDate(_ value: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)!
    }
}
