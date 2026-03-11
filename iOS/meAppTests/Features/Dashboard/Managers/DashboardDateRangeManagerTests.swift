import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardDateRangeManagerTests {
    private func makeSUT() -> DashboardDateRangeManager {
        DashboardDateRangeManager()
    }

    @Test("year label range: aligns to month start and spans 12 inclusive months")
    func yearLabelRangeAlignsToMonthWindow() {
        let sut = makeSUT()
        let scroll = date("2026-03-15")

        let range = sut.getYearLabelDateRange(xScrollPosition: scroll)

        #expect(range?.start == date("2026-03-01"))
        #expect(range?.end == sut.inclusiveEnd(fromExclusive: date("2027-03-01")))
    }

    @Test("month label range: full contained month returns full calendar month")
    func monthLabelRangeUsesFullyContainedMonth() {
        let sut = makeSUT()
        let scroll = date("2026-02-01")

        let range = sut.getLabelDateRangeForMonth(
            xScrollPosition: scroll,
            visibleDomainLength: 35 * 24 * 60 * 60,
            continuousOperations: []
        )

        #expect(range.start == date("2026-02-01"))
        #expect(range.end == sut.inclusiveEnd(fromExclusive: date("2026-03-01")))
    }

    @Test("month label range: future month without entries clamps to current month end")
    func monthLabelRangeClampsAtCurrentMonthEnd() {
        let sut = makeSUT()
        let calendar = Calendar.current
        let today = Date()
        let currentMonth = calendar.dateInterval(of: .month, for: today)!
        let scroll = calendar.date(byAdding: .day, value: 20, to: currentMonth.start)!
        let lastEntry = calendar.date(byAdding: .day, value: 5, to: currentMonth.start)!

        let range = sut.getLabelDateRangeForMonth(
            xScrollPosition: scroll,
            visibleDomainLength: 25 * 24 * 60 * 60,
            continuousOperations: [DashboardTestFixtures.makeSummary(date: lastEntry)]
        )

        #expect(range.end == sut.inclusiveEnd(fromExclusive: currentMonth.end))
    }

    @Test("year label range: year wrapper mirrors the aligned year window")
    func yearDateIntervalUsesYearLabelRange() {
        let sut = makeSUT()
        let scroll = date("2026-05-20")

        let range = sut.getLabelDateRangeForYear(
            xScrollPosition: scroll,
            visibleDomainLength: 365 * 24 * 60 * 60
        )

        #expect(range.start == date("2026-05-01"))
        #expect(range.end == sut.inclusiveEnd(fromExclusive: date("2027-05-01")))
    }

    @Test("week label range: starts at day boundary and includes six more days")
    func weekLabelRangeStartsAtDayBoundary() {
        let sut = makeSUT()
        let scroll = isoDate("2026-03-10T14:30:00Z")

        let range = sut.getLabelDateRangeForWeek(xScrollPosition: scroll)

        #expect(range.start == date("2026-03-10"))
        #expect(range.end.timeIntervalSince(range.start) == (7 * 24 * 60 * 60) - 1)
    }

    @Test("fully contained month interval: can resolve the next month when left edge is previous month end")
    func fullyContainedMonthIntervalFindsNextMonth() {
        let sut = makeSUT()

        let interval = sut.getFullyContainedMonthInterval(
            xScrollPosition: isoDate("2026-01-31T12:00:00Z"),
            visibleDomainLength: 32 * 24 * 60 * 60
        )

        #expect(interval?.start == date("2026-02-01"))
        #expect(interval?.end == date("2026-03-01"))
    }

    @Test("total period label: uses date bounds or fallback when empty")
    func totalLabelUsesBoundsOrFallback() {
        let sut = makeSUT()

        let withBounds = sut.labelForTotalPeriod(
            dateBounds: (min: date("2026-01-01"), max: date("2026-03-31")),
            formatDateRange: { start, end, period in
                "\(period.rawValue):\(isoDay(start)):\(isoDay(end))"
            },
            fallbackLabel: { "fallback" }
        )
        let withoutBounds = sut.labelForTotalPeriod(
            dateBounds: nil,
            formatDateRange: { _, _, _ in "unexpected" },
            fallbackLabel: { "fallback" }
        )

        #expect(withBounds == "total:2026-01-01:2026-03-31")
        #expect(withoutBounds == "fallback")
    }

    @Test("gridline labels: month week and year pass the expected ranges to the formatter closure")
    func gridlineLabelsUseExpectedRanges() {
        let sut = makeSUT()
        let month = sut.labelForMonthGridlines(
            xScrollPosition: date("2026-03-01"),
            visibleDomainLength: 35 * 24 * 60 * 60,
            continuousOperations: []
        ) { start, end, period in
            "\(period.rawValue):\(isoDay(start)):\(isoDay(end))"
        }
        let week = sut.labelForWeekGridlines(xScrollPosition: isoDate("2026-03-10T14:30:00Z")) { start, end, period in
            "\(period.rawValue):\(isoDay(start)):\(isoDay(end))"
        }
        let year = sut.labelForYearGridlines(xScrollPosition: date("2026-03-15")) { start, end, period in
            "\(period.rawValue):\(isoDay(start)):\(isoDay(end))"
        } fallbackLabel: {
            "fallback"
        }

        #expect(month == "month:2026-03-01:2026-03-31")
        #expect(week == "week:2026-03-10:2026-03-17")
        #expect(year == "year:2026-03-01:2027-02-28")
    }

    @Test("default range label: delegates to the shared formatter for every period")
    func defaultRangeLabelDelegates() {
        let sut = makeSUT()

        let label = sut.defaultRangeLabel(
            for: .week,
            lastScrollPosition: date("2026-03-01"),
            visibleDomainLength: 7 * 24 * 60 * 60
        ) { start, end, period in
            "\(period.rawValue):\(isoDay(start)):\(isoDay(end))"
        }

        #expect(label == "week:2026-03-01:2026-03-08")
    }

    @Test("week range label: formats same month, cross month, and cross year windows")
    func weekRangeFormattingVariants() {
        let sut = makeSUT()

        #expect(sut.formatWeekRangeLabel(from: date("2026-03-01"), to: date("2026-03-07")) == "Mar 1 – 7, 2026")
        #expect(sut.formatWeekRangeLabel(from: date("2026-03-30"), to: date("2026-04-05")) == "Mar 30 – Apr 5, 2026")
        #expect(sut.formatWeekRangeLabel(from: date("2026-12-28"), to: date("2027-01-03")) == "Dec 28, 2026 – Jan 3, 2027")
    }

    @Test("empty state labels: return period-specific defaults")
    func emptyStateLabels() {
        let sut = makeSUT()
        let today = date("2026-03-10")

        #expect(sut.emptyStatePeriodLabel(for: .week, today: today) == "Mar 8 - Mar 14, 2026")
        #expect(sut.emptyStatePeriodLabel(for: .month, today: today) == "Mar, 2026")
        #expect(sut.emptyStatePeriodLabel(for: .year, today: today) == "2026")
        #expect(sut.emptyStatePeriodLabel(for: .total, today: today) == "2026")
    }

    @Test("filter operations in date range: returns ordered slice and handles empty data")
    func filterOperationsInDateRange() {
        let sut = makeSUT()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()

        let filtered = sut.filterOperationsInDateRange(
            operations: ops,
            start: date("2026-03-02"),
            end: date("2026-03-04")
        )

        #expect(filtered.map(\.period) == ["2026-03-02", "2026-03-03", "2026-03-04"])
        #expect(sut.filterOperationsInDateRange(operations: [], start: date("2026-03-01"), end: date("2026-03-02")).isEmpty)
    }

    @Test("filter operations by day: includes entries across the whole day boundary")
    func filterOperationsInDateRangeByDay() {
        let sut = makeSUT()
        let ops = [
            DashboardTestFixtures.makeSummary(period: "2026-03-01", date: isoDate("2026-03-01T08:00:00Z")),
            DashboardTestFixtures.makeSummary(period: "2026-03-02", date: isoDate("2026-03-02T23:00:00Z")),
            DashboardTestFixtures.makeSummary(period: "2026-03-03", date: isoDate("2026-03-03T01:00:00Z"))
        ]

        let filtered = sut.filterOperationsInDateRangeByDay(
            operations: ops,
            start: isoDate("2026-03-02T00:01:00Z"),
            end: isoDate("2026-03-03T00:01:00Z")
        )

        #expect(filtered.map(\.period) == ["2026-03-02", "2026-03-03"])
    }

    @Test("operations for label date range: uses cache when period and scroll threshold match")
    func operationsForLabelDateRangeUsesCache() {
        let sut = makeSUT()
        let cached = [DashboardTestFixtures.makeSummary(period: "2026-03-01")]
        let result = sut.getOperationsForLabelDateRange(
            period: .month,
            xScrollPosition: isoDate("2026-03-01T12:00:00Z"),
            visibleDomainLength: { _ in 30 * 24 * 60 * 60 },
            continuousOperations: DashboardTestFixtures.makeSortedDailySummaries(),
            dateBounds: nil,
            cachedPeriod: .month,
            cachedScrollPos: isoDate("2026-03-01T00:00:00Z"),
            cachedOps: cached
        )

        #expect(result.operations.map(\.period) == ["2026-03-01"])
        #expect(result.cachedOps.map(\.period) == ["2026-03-01"])
    }

    @Test("operations for label date range: filters per week year and total ranges")
    func operationsForLabelDateRangeFiltersPerPeriod() {
        let sut = makeSUT()
        let weekOps = DashboardTestFixtures.makeSortedDailySummaries()
        let yearOps = [
            DashboardTestFixtures.makeSummary(period: "2025-12", date: date("2025-12-01")),
            DashboardTestFixtures.makeSummary(period: "2026-03", date: date("2026-03-01")),
            DashboardTestFixtures.makeSummary(period: "2027-04", date: date("2027-04-01"))
        ]

        let week = sut.getOperationsForLabelDateRange(
            period: .week,
            xScrollPosition: date("2026-03-02"),
            visibleDomainLength: { _ in 0 },
            continuousOperations: weekOps,
            dateBounds: nil,
            cachedPeriod: nil,
            cachedScrollPos: nil,
            cachedOps: []
        )
        let year = sut.getOperationsForLabelDateRange(
            period: .year,
            xScrollPosition: date("2026-03-01"),
            visibleDomainLength: { _ in 365 * 24 * 60 * 60 },
            continuousOperations: yearOps,
            dateBounds: nil,
            cachedPeriod: nil,
            cachedScrollPos: nil,
            cachedOps: []
        )
        let total = sut.getOperationsForLabelDateRange(
            period: .total,
            xScrollPosition: date("2026-03-01"),
            visibleDomainLength: { _ in 0 },
            continuousOperations: weekOps,
            dateBounds: (min: date("2026-03-02"), max: date("2026-03-04")),
            cachedPeriod: nil,
            cachedScrollPos: nil,
            cachedOps: []
        )

        #expect(week.operations.map { $0.period } == ["2026-03-02", "2026-03-03", "2026-03-04", "2026-03-05"])
        #expect(year.operations.map { $0.period } == ["2026-03"])
        #expect(total.operations.map { $0.period } == ["2026-03-02", "2026-03-03", "2026-03-04"])
    }

    private func date(_ value: String) -> Date {
        DateTimeTools.getDateFromDateString(value, format: "yyyy-MM-dd")
    }

    private func isoDate(_ value: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) {
            return date
        }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)!
    }

    private func isoDay(_ date: Date) -> String {
        DateTimeTools.formatter("yyyy-MM-dd").string(from: date)
    }
}
