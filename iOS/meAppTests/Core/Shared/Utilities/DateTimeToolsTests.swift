import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
struct DateTimeToolsArrivalRelativeTimeTests {

    /// Fixed anchor: Apr 30, 2026, 09:34 AM in the device's current calendar/timezone.
    private static func anchorNow() -> Date {
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 4
        comps.day = 30
        comps.hour = 9
        comps.minute = 34
        comps.second = 0
        return Calendar.current.date(from: comps)!
    }

    @Test("Returns 'Just now' when date is in the future")
    func futureCollapsesToJustNow() {
        let now = Self.anchorNow()
        let future = now.addingTimeInterval(120)
        #expect(DateTimeTools.getArrivalRelativeTime(future, now: now) == DashboardStrings.justNow)
    }

    @Test("Returns 'Just now' for delta < 60s")
    func subMinuteIsJustNow() {
        let now = Self.anchorNow()
        #expect(DateTimeTools.getArrivalRelativeTime(now, now: now) == DashboardStrings.justNow)
        #expect(DateTimeTools.getArrivalRelativeTime(now.addingTimeInterval(-30), now: now) == DashboardStrings.justNow)
        #expect(DateTimeTools.getArrivalRelativeTime(now.addingTimeInterval(-59), now: now) == DashboardStrings.justNow)
    }

    @Test("Returns '1 min ago' at 60s")
    func oneMinuteAgo() {
        let now = Self.anchorNow()
        let date = now.addingTimeInterval(-60)
        #expect(DateTimeTools.getArrivalRelativeTime(date, now: now) == DashboardStrings.oneMinuteAgo)
    }

    @Test("Returns 'X min ago' for 2..<60 min")
    func minutesAgo() {
        let now = Self.anchorNow()
        #expect(DateTimeTools.getArrivalRelativeTime(now.addingTimeInterval(-120), now: now) == "2 min ago")
        #expect(DateTimeTools.getArrivalRelativeTime(now.addingTimeInterval(-3540), now: now) == "59 min ago")
    }

    @Test("Returns time-of-day at >= 60 min same day")
    func sameDayAfterAnHour() {
        let now = Self.anchorNow()
        // 60 min back → 8:34 AM
        let date = now.addingTimeInterval(-3600)
        let result = DateTimeTools.getArrivalRelativeTime(date, now: now)
        #expect(result == "8:34 AM")
    }

    @Test("Returns 'Yesterday <h:mm a>' for yesterday")
    func yesterday() {
        let now = Self.anchorNow()
        // Apr 29, 2026 11:00 PM
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 4
        comps.day = 29
        comps.hour = 23
        comps.minute = 0
        let date = Calendar.current.date(from: comps)!
        let result = DateTimeTools.getArrivalRelativeTime(date, now: now)
        #expect(result == "Yesterday 11:00 PM")
    }

    @Test("Returns 'MMM d, h:mm a' for same year, older than yesterday")
    func sameYearOlder() {
        let now = Self.anchorNow()
        // Apr 12, 2026 9:34 AM
        var comps = DateComponents()
        comps.year = 2026
        comps.month = 4
        comps.day = 12
        comps.hour = 9
        comps.minute = 34
        let date = Calendar.current.date(from: comps)!
        let result = DateTimeTools.getArrivalRelativeTime(date, now: now)
        #expect(result == "Apr 12, 9:34 AM")
    }

    @Test("Returns 'MMM d, yyyy' for prior years")
    func priorYear() {
        let now = Self.anchorNow()
        // Apr 12, 2024
        var comps = DateComponents()
        comps.year = 2024
        comps.month = 4
        comps.day = 12
        comps.hour = 9
        comps.minute = 34
        let date = Calendar.current.date(from: comps)!
        let result = DateTimeTools.getArrivalRelativeTime(date, now: now)
        #expect(result == "Apr 12, 2024")
    }

    @Test("ISO-string overload returns nil for empty / unparseable input")
    func isoStringNilSafety() {
        #expect(DateTimeTools.getArrivalRelativeTime(fromISOString: "") == nil)
        #expect(DateTimeTools.getArrivalRelativeTime(fromISOString: "not-a-date") == nil)
    }

    @Test("ISO-string overload formats a real timestamp")
    func isoStringHappyPath() {
        let now = Self.anchorNow()
        let iso = DateTimeTools.isoFormatter().string(from: now.addingTimeInterval(-300))
        let result = DateTimeTools.getArrivalRelativeTime(fromISOString: iso, now: now)
        #expect(result == "5 min ago")
    }
}
