import Foundation
@testable import meApp
import Testing

/// Covers `TimePeriod.periodStart(for:calendar:)` — the shared period-boundary snapping that the
/// baby chart's X-axis `domainMin` and the reference-curve start both depend on (MOB-626). Uses a
/// fixed UTC Gregorian calendar so the assertions are deterministic across machines/time zones.
@Suite
struct TimePeriodTests {
    private func gregorianUTC() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int, in calendar: Calendar) throws -> Date {
        try #require(calendar.date(from: DateComponents(year: year, month: month, day: day)))
    }

    @Test("week snaps back to the preceding Sunday")
    func weekSnapsToPrecedingSunday() throws {
        let calendar = gregorianUTC()
        // 2026-03-11 is a Wednesday; its Sunday-first week starts 2026-03-08.
        let wednesday = try date(2026, 3, 11, in: calendar)
        let start = TimePeriod.week.periodStart(for: wednesday, calendar: calendar)
        let comps = calendar.dateComponents([.year, .month, .day, .weekday], from: start)
        #expect(comps.weekday == 1) // Sunday
        #expect(comps.year == 2026)
        #expect(comps.month == 3)
        #expect(comps.day == 8)
    }

    @Test("week ignores the calendar's firstWeekday and always uses Sunday")
    func weekIgnoresCalendarFirstWeekday() throws {
        var calendar = gregorianUTC()
        calendar.firstWeekday = 2 // Monday — periodStart must still snap to Sunday
        let wednesday = try date(2026, 3, 11, in: calendar)
        let start = TimePeriod.week.periodStart(for: wednesday, calendar: calendar)
        let comps = calendar.dateComponents([.weekday, .day], from: start)
        #expect(comps.weekday == 1) // Sunday, not Monday
        #expect(comps.day == 8)
    }

    @Test("month snaps to the 1st")
    func monthSnapsToFirstOfMonth() throws {
        let calendar = gregorianUTC()
        let midMonth = try date(2026, 3, 17, in: calendar)
        let start = TimePeriod.month.periodStart(for: midMonth, calendar: calendar)
        let comps = calendar.dateComponents([.year, .month, .day], from: start)
        #expect(comps.year == 2026)
        #expect(comps.month == 3)
        #expect(comps.day == 1)
    }

    @Test("year snaps to Jan 1")
    func yearSnapsToJanuaryFirst() throws {
        let calendar = gregorianUTC()
        let midYear = try date(2026, 7, 20, in: calendar)
        let start = TimePeriod.year.periodStart(for: midYear, calendar: calendar)
        let comps = calendar.dateComponents([.year, .month, .day], from: start)
        #expect(comps.year == 2026)
        #expect(comps.month == 1)
        #expect(comps.day == 1)
    }

    @Test("total returns the date unchanged")
    func totalReturnsDateUnchanged() throws {
        let calendar = gregorianUTC()
        let date = try date(2026, 7, 20, in: calendar)
        #expect(TimePeriod.total.periodStart(for: date, calendar: calendar) == date)
    }
}
