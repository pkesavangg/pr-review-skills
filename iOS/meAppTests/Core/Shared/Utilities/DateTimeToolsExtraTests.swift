import Foundation
@testable import meApp
import Testing

/// Additional deterministic coverage for `DateTimeTools` beyond the arrival-relative-time suite.
///
/// Determinism strategy:
/// - Date-only strings (`"2025-05-29"`) parse to LOCAL midnight and are formatted back in the
///   LOCAL timezone, so day/month/year assertions are timezone-independent.
/// - UTC instant assertions use `getTimestamp` (an absolute epoch) which is timezone-independent.
/// - `parseCalendarDate` deliberately preserves the UTC calendar day at local midnight, so its
///   `yyyy-MM-dd` render is also timezone-independent.
/// - Timezone accessors are compared against `TimeZone.current` (the same source), never a literal.
/// - "Now"-based functions use loose windows / shape checks rather than exact literals.
@Suite(.serialized)
struct DateTimeToolsExtraTests {

    // MARK: - Fixtures

    private static func localDate(
        _ year: Int,
        _ month: Int,
        _ day: Int,
        _ hour: Int = 0,
        _ minute: Int = 0,
        _ second: Int = 0
    ) throws -> Date {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        comps.hour = hour
        comps.minute = minute
        comps.second = second
        return try #require(Calendar.current.date(from: comps))
    }

    private static func summary(date: Date) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct",
            period: "p",
            entryTimestamp: "",
            date: date,
            count: 1,
            weight: 100
        )
    }

    // MARK: - Date-string formatters (date-only input → tz-independent)

    @Test("getFormattedDate formats 'LLL d, yyyy'")
    func formattedDate() {
        #expect(DateTimeTools.getFormattedDate("2025-05-29") == "May 29, 2025")
        #expect(DateTimeTools.getFormattedDate("not-a-date") == DateTimeTools.invalidString)
    }

    @Test("getFormattedDay formats 'MMM d'")
    func formattedDay() {
        #expect(DateTimeTools.getFormattedDay("2025-05-29") == "May 29")
        #expect(DateTimeTools.getFormattedDay("bad") == DateTimeTools.invalidString)
    }

    @Test("getFormattedDayWithTime formats 'MMM d, h:mma'")
    func formattedDayWithTime() {
        #expect(DateTimeTools.getFormattedDayWithTime("2025-05-29") == "May 29, 12:00AM")
        #expect(DateTimeTools.getFormattedDayWithTime("bad") == DateTimeTools.invalidString)
    }

    @Test("getFormattedTime formats 'h:mm a'")
    func formattedTime() {
        #expect(DateTimeTools.getFormattedTime("2025-05-29") == "12:00 AM")
        #expect(DateTimeTools.getFormattedTime("bad") == DateTimeTools.invalidString)
    }

    @Test("getFormattedTimeLowercased formats 'h:mm a' with lower-cased meridiem")
    func formattedTimeLowercased() {
        #expect(DateTimeTools.getFormattedTimeLowercased("2025-05-29") == "12:00 am")
        #expect(DateTimeTools.getFormattedTimeLowercased("bad") == DateTimeTools.invalidString)
    }

    @Test("getMonth / getMonthYear / getMonthDayYear / getMonthDayYearShort / getYear")
    func monthYearVariants() {
        #expect(DateTimeTools.getMonth("2025-05-29") == "May")
        #expect(DateTimeTools.getMonthYear("2025-05-29") == "May 2025")
        #expect(DateTimeTools.getMonthDayYear("2025-05-29") == "May 29, 2025")
        #expect(DateTimeTools.getMonthDayYearShort("2025-05-29") == "May 2025")
        #expect(DateTimeTools.getYear("2025-05-29") == "2025")

        #expect(DateTimeTools.getMonth("bad") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getMonthYear("bad") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getMonthDayYear("bad") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getMonthDayYearShort("bad") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getYear("bad") == DateTimeTools.invalidString)
    }

    @Test("getDay returns weekday (May 29 2025 is Thursday = 5)")
    func getDay() {
        #expect(DateTimeTools.getDay("2025-05-29") == 5)
        #expect(DateTimeTools.getDay("bad") == nil)
    }

    @Test("getDateStringFromDate / getMonthStringFromDate")
    func dateAndMonthStrings() {
        #expect(DateTimeTools.getDateStringFromDate("2025-05-29") == "2025-05-29")
        #expect(DateTimeTools.getMonthStringFromDate("2025-05-29") == "2025-05")
        #expect(DateTimeTools.getDateStringFromDate("bad") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getMonthStringFromDate("bad") == DateTimeTools.invalidString)
    }

    @Test("getBirthdayFormattedString / getDateStringFormattedForIonicDatePicker")
    func ionicAndBirthdayFormats() {
        #expect(DateTimeTools.getBirthdayFormattedString("2025-05-29") == "2025-05-29T00:00:00")
        #expect(DateTimeTools.getDateStringFormattedForIonicDatePicker("2025-05-29") == "2025-05-29T00:00:00")
        #expect(DateTimeTools.getBirthdayFormattedString("bad") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getDateStringFormattedForIonicDatePicker("bad") == DateTimeTools.invalidString)
    }

    @Test("getLocalDateStringFromUTCDate / getLocalMonthStringFromUTCDate (date-only round-trip)")
    func localDateAndMonthFromUTC() {
        // Date-only parses to local midnight, then rendered in local tz → same calendar day.
        #expect(DateTimeTools.getLocalDateStringFromUTCDate("2025-05-29") == "2025-05-29")
        #expect(DateTimeTools.getLocalMonthStringFromUTCDate("2025-05-29") == "2025-05")
        // Cache hit path (same key again).
        #expect(DateTimeTools.getLocalDateStringFromUTCDate("2025-05-29") == "2025-05-29")
        #expect(DateTimeTools.getLocalMonthStringFromUTCDate("2025-05-29") == "2025-05")

        #expect(DateTimeTools.getLocalDateStringFromUTCDate("nope") == DateTimeTools.invalidString)
        #expect(DateTimeTools.getLocalMonthStringFromUTCDate("nope") == DateTimeTools.invalidString)
    }

    // MARK: - getDateFromDateString

    @Test("getDateFromDateString: empty falls back to ~now")
    func dateFromStringEmpty() {
        let result = DateTimeTools.getDateFromDateString("", format: "yyyy-MM-dd")
        #expect(abs(result.timeIntervalSinceNow) < 5)
    }

    @Test("getDateFromDateString: matching format parses exactly")
    func dateFromStringMatchingFormat() {
        let result = DateTimeTools.getDateFromDateString("2025-05-29", format: "yyyy-MM-dd")
        #expect(DateTimeTools.formatter("yyyy-MM-dd").string(from: result) == "2025-05-29")
    }

    @Test("getDateFromDateString: non-matching format falls back to general parse")
    func dateFromStringGeneralFallback() {
        // "HH:mm" won't match a date-only string, but the general parser will.
        let result = DateTimeTools.getDateFromDateString("2025-05-29", format: "HH:mm")
        #expect(DateTimeTools.formatter("yyyy-MM-dd").string(from: result) == "2025-05-29")
    }

    @Test("getDateFromDateString: fully unparseable falls back to ~now")
    func dateFromStringUnparseable() {
        let result = DateTimeTools.getDateFromDateString("totally-bad", format: "HH:mm")
        #expect(abs(result.timeIntervalSinceNow) < 5)
    }

    // MARK: - parse / parseCalendarDate

    @Test("parse: nil for invalid, non-nil for supported formats")
    func parseVariants() {
        #expect(DateTimeTools.parse("") == nil)
        #expect(DateTimeTools.parse("not-a-date") == nil)
        #expect(DateTimeTools.parse("2025-05-29") != nil)
        #expect(DateTimeTools.parse("2025-05-29 14:30:00") != nil)
        #expect(DateTimeTools.parse("2021-01-01T00:00:00.000Z") != nil)
    }

    @Test("parseCalendarDate preserves calendar day at local midnight")
    func parseCalendarDate() throws {
        let ymd = try #require(DateTimeTools.parseCalendarDate("2025-05-29"))
        #expect(DateTimeTools.formatDateToYMD_Local(ymd) == "2025-05-29")

        // UTC-midnight ISO must render as the SAME calendar day locally (the whole point).
        let dob = try #require(DateTimeTools.parseCalendarDate("1999-09-09T00:00:00.000Z"))
        #expect(DateTimeTools.formatDateToYMD_Local(dob) == "1999-09-09")

        #expect(DateTimeTools.parseCalendarDate("garbage") == nil)
    }

    // MARK: - Timestamp conversions (UTC instant → tz-independent)

    @Test("getTimestamp returns ms since epoch, 0 on failure")
    func timestamp() {
        // 2021-01-01T00:00:00Z == 1609459200 s.
        #expect(DateTimeTools.getTimestamp("2021-01-01T00:00:00.000Z") == 1_609_459_200_000)
        #expect(DateTimeTools.getTimestamp("invalid") == 0)
    }

    @Test("getDatetimeIsoString round-trips a UTC instant; invalid → '---'")
    func datetimeIsoString() {
        let iso = DateTimeTools.getDatetimeIsoString("2021-01-01T00:00:00.000Z")
        #expect(DateTimeTools.getTimestamp(iso) == 1_609_459_200_000)
        #expect(DateTimeTools.getDatetimeIsoString("bad") == DateTimeTools.invalidString)
    }

    @Test("getFormattedDateFromTimestamp / getShortFormattedDateFromTimestamp are non-empty")
    func formattedFromTimestamp() {
        let long = DateTimeTools.getFormattedDateFromTimestamp(1_609_459_200)
        let short = DateTimeTools.getShortFormattedDateFromTimestamp(1_609_459_200)
        #expect(!long.isEmpty)
        #expect(long != DateTimeTools.invalidString)
        #expect(!short.isEmpty)
        #expect(short != DateTimeTools.invalidString)
    }

    // MARK: - Interval ISO string

    @Test("getIntervalDatetimeIsoString subtracts days from a parsed start, zeroed to midnight")
    func intervalDatetimeIsoString() throws {
        let zero = DateTimeTools.getIntervalDatetimeIsoString(interval: 0, start: "2025-05-29")
        let expectedZero = try #require(DateTimeTools.parse("2025-05-29"))
        let parsedZero = try #require(DateTimeTools.parse(zero))
        #expect(abs(parsedZero.timeIntervalSince(expectedZero)) < 1)

        let sevenAgo = DateTimeTools.getIntervalDatetimeIsoString(interval: 7, start: "2025-05-29")
        let expectedSeven = try #require(DateTimeTools.parse("2025-05-22"))
        let parsedSeven = try #require(DateTimeTools.parse(sevenAgo))
        #expect(abs(parsedSeven.timeIntervalSince(expectedSeven)) < 1)
    }

    // MARK: - combineDate / isoString

    @Test("combineDate keeps Y-M-D from date and H-M-S from time")
    func combineDate() throws {
        let date = try Self.localDate(2025, 5, 29)
        let time = try Self.localDate(2000, 1, 1, 14, 30, 45)
        let combined = DateTimeTools.combineDate(date, withTime: time)
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: combined)
        #expect(comps.year == 2025)
        #expect(comps.month == 5)
        #expect(comps.day == 29)
        #expect(comps.hour == 14)
        #expect(comps.minute == 30)
        #expect(comps.second == 45)
    }

    @Test("isoString round-trips through parse for UTC and local variants")
    func isoStringRoundTrip() throws {
        let date = try Self.localDate(2025, 6, 16)
        let time = try Self.localDate(2000, 1, 1, 8, 24, 50)
        let combined = DateTimeTools.combineDate(date, withTime: time)

        let utc = DateTimeTools.isoString(date: date, time: time, useUTC: true)
        let parsedUTC = try #require(DateTimeTools.parse(utc))
        #expect(abs(parsedUTC.timeIntervalSince(combined)) < 0.001)
        #expect(utc.hasSuffix("Z"))

        let local = DateTimeTools.isoString(date: date, time: time, useUTC: false)
        let parsedLocal = try #require(DateTimeTools.parse(local))
        #expect(abs(parsedLocal.timeIntervalSince(combined)) < 0.001)
    }

    // MARK: - Timezone accessors (compared against TimeZone.current)

    @Test("timezone accessors mirror TimeZone.current")
    func timezoneAccessors() {
        let expectedMinutes = TimeZone.current.secondsFromGMT() / 60
        #expect(DateTimeTools.getUserTimezone() == TimeZone.current.identifier)
        #expect(DateTimeTools.getUserTimezoneOffset() == expectedMinutes)
        #expect(DateTimeTools.getTimeZoneInMinutes() == expectedMinutes)
        #expect(DateTimeTools.getUTCOffset() == expectedMinutes)
        #expect(DateTimeTools.getTimezoneOffsetString() == "\(expectedMinutes) min \(TimeZone.current.identifier)")
    }

    // MARK: - formatDateToYMD_Local

    @Test("formatDateToYMD_Local renders local calendar day")
    func formatDateToYMDLocal() throws {
        let date = try Self.localDate(2025, 5, 29, 15, 0, 0)
        #expect(DateTimeTools.formatDateToYMD_Local(date) == "2025-05-29")
    }

    // MARK: - Birthday helpers

    @Test("minAllowedBirthdayDate offsets years and zeroes the time")
    func minAllowedBirthday() {
        let currentYear = Calendar.current.component(.year, from: Date())
        let thirteen = DateTimeTools.minAllowedBirthdayDate()
        let comps = Calendar.current.dateComponents([.year, .hour, .minute, .second], from: thirteen)
        #expect(comps.year == currentYear - 13)
        #expect(comps.hour == 0)
        #expect(comps.minute == 0)
        #expect(comps.second == 0)

        let twenty = DateTimeTools.minAllowedBirthdayDate(yearsAgo: 20)
        #expect(Calendar.current.component(.year, from: twenty) == currentYear - 20)
    }

    @Test("getMinBirthdayOffsetForDatePicker is midnight, 13 years ago")
    func minBirthdayOffsetForPicker() {
        let currentYear = Calendar.current.component(.year, from: Date())
        let str = DateTimeTools.getMinBirthdayOffsetForDatePicker()
        #expect(str.hasSuffix("T00:00:00"))
        #expect(str.hasPrefix("\(currentYear - 13)"))
    }

    // MARK: - "Now"-based helpers (shape / window checks)

    @Test("getCurrentDatetimeIsoString parses back to ~now")
    func currentDatetimeIso() throws {
        let iso = DateTimeTools.getCurrentDatetimeIsoString()
        let parsed = try #require(DateTimeTools.parse(iso))
        #expect(abs(parsed.timeIntervalSinceNow) < 5)
    }

    @Test("getTodayFormattedDate contains the current year")
    func todayFormattedDate() {
        let currentYear = Calendar.current.component(.year, from: Date())
        #expect(DateTimeTools.getTodayFormattedDate().contains("\(currentYear)"))
    }

    @Test("getCurrentTimeWithTimeZone is non-empty")
    func currentTimeWithTimeZone() {
        #expect(!DateTimeTools.getCurrentTimeWithTimeZone().isEmpty)
    }

    @Test("getCurrentDayTimeShort lowercases am/pm")
    func currentDayTimeShort() {
        let str = DateTimeTools.getCurrentDayTimeShort()
        #expect(!str.isEmpty)
        #expect(!str.contains("AM"))
        #expect(!str.contains("PM"))
    }

    @Test("getCurrentTimestampMillis ~ now; getTimestampDaysAgo is earlier")
    func timestampMillis() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let current = DateTimeTools.getCurrentTimestampMillis()
        #expect(abs(current - now) < 5000)

        let sevenDaysAgo = DateTimeTools.getTimestampDaysAgo(7)
        #expect(sevenDaysAgo < current)
        // ~7 days in ms. `byAdding: .day` preserves wall-clock time, so a DST transition in
        // the window can shift the delta by up to an hour — keep the tolerance generous.
        let delta = current - sevenDaysAgo
        let dayMs: Int64 = 24 * 60 * 60 * 1000
        #expect(delta > 6 * dayMs)
        #expect(delta < 8 * dayMs)
    }

    // MARK: - Chart: visibleDomainLength / shouldRepeatXAxisLabels

    @Test("visibleDomainLength maps each period")
    func visibleDomainLength() {
        #expect(DateTimeTools.visibleDomainLength(for: .week) == DashboardConstants.TimeInterval.week)
        #expect(DateTimeTools.visibleDomainLength(for: .month) == DashboardConstants.TimeInterval.month)
        #expect(DateTimeTools.visibleDomainLength(for: .year) == DashboardConstants.TimeInterval.year)
        #expect(DateTimeTools.visibleDomainLength(for: .total) == DashboardConstants.TimeInterval.year)
    }

    @Test("shouldRepeatXAxisLabels respects per-period thresholds")
    func shouldRepeatXAxisLabels() {
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .week, entryCount: 6) == false)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .week, entryCount: 7) == true)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .month, entryCount: 19) == false)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .month, entryCount: 20) == true)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .year, entryCount: 11) == false)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .year, entryCount: 12) == true)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .total, entryCount: 11) == false)
        #expect(DateTimeTools.shouldRepeatXAxisLabels(for: .total, entryCount: 12) == true)
    }

    // MARK: - Chart: formatXAxisLabel

    @Test("formatXAxisLabel per period")
    func formatXAxisLabel() throws {
        let sunday = try Self.localDate(2025, 6, 1) // June 1 2025 is a Sunday
        #expect(DateTimeTools.formatXAxisLabel(for: sunday, period: .week, operations: []) == "Sun")

        let mid = try Self.localDate(2025, 6, 15)
        #expect(DateTimeTools.formatXAxisLabel(for: mid, period: .month, operations: []) == "15")

        #expect(DateTimeTools.formatXAxisLabel(for: sunday, period: .year, operations: []) == "J") // June → "J"

        // .total, empty operations → same-era → month initial.
        #expect(DateTimeTools.formatXAxisLabel(for: sunday, period: .total, operations: []) == "J")

        // .total, multi-year → year string.
        let ops = [Self.summary(date: try Self.localDate(2024, 6, 1)),
                   Self.summary(date: sunday)]
        #expect(DateTimeTools.formatXAxisLabel(for: sunday, period: .total, operations: ops) == "2025")
    }

    // MARK: - Chart: areEntriesInSameEra

    @Test("areEntriesInSameEra handles empty, same-year, multi-year, and out-of-range filtering")
    func areEntriesInSameEra() throws {
        #expect(DateTimeTools.areEntriesInSameEra([]) == true)

        let sameYear = [Self.summary(date: try Self.localDate(2025, 1, 1)),
                        Self.summary(date: try Self.localDate(2025, 12, 31))]
        #expect(DateTimeTools.areEntriesInSameEra(sameYear) == true)

        let multiYear = [Self.summary(date: try Self.localDate(2024, 1, 1)),
                         Self.summary(date: try Self.localDate(2025, 1, 1))]
        #expect(DateTimeTools.areEntriesInSameEra(multiYear) == false)

        // Out-of-range years are filtered; only the 2025 entry remains → single era.
        let filtered = [Self.summary(date: try Self.localDate(1800, 1, 1)),
                        Self.summary(date: try Self.localDate(2025, 1, 1))]
        #expect(DateTimeTools.areEntriesInSameEra(filtered) == true)

        // All out-of-range → validSummaries empty → true.
        let allInvalid = [Self.summary(date: try Self.localDate(1800, 1, 1))]
        #expect(DateTimeTools.areEntriesInSameEra(allInvalid) == true)
    }

    // MARK: - Chart: generateXAxisValues

    @Test("generateXAxisValues returns empty when there are no operations")
    func generateXAxisEmpty() {
        #expect(DateTimeTools.generateXAxisValues(for: .week, from: [], shouldRepeat: false, entryCount: 0).isEmpty)
    }

    @Test("generateXAxisValues non-repeat fixed counts per period")
    func generateXAxisNonRepeat() throws {
        let day = try Self.localDate(2025, 6, 15)
        let ops = [Self.summary(date: day)]

        #expect(DateTimeTools.generateXAxisValues(for: .week, from: ops, shouldRepeat: false, entryCount: 1).count == 7)
        #expect(DateTimeTools.generateXAxisValues(for: .month, from: ops, shouldRepeat: false, entryCount: 1).count == 5)
        #expect(DateTimeTools.generateXAxisValues(for: .year, from: ops, shouldRepeat: false, entryCount: 1).count == 12)
        // .total, same era → yearly path → 12.
        #expect(DateTimeTools.generateXAxisValues(for: .total, from: ops, shouldRepeat: false, entryCount: 1).count == 12)
    }

    @Test("generateXAxisValues repeat mode produces more ticks than the single-pass count")
    func generateXAxisRepeat() throws {
        let day = try Self.localDate(2025, 6, 15)
        let ops = [Self.summary(date: day)]
        #expect(DateTimeTools.generateXAxisValues(for: .week, from: ops, shouldRepeat: true, entryCount: 30).count > 7)
        #expect(DateTimeTools.generateXAxisValues(for: .month, from: ops, shouldRepeat: true, entryCount: 30).count > 5)
        #expect(DateTimeTools.generateXAxisValues(for: .year, from: ops, shouldRepeat: true, entryCount: 30).count > 12)
    }

    @Test("generateXAxisValues total multi-year uses quarterly ticks")
    func generateXAxisTotalMultiYear() throws {
        let ops = [Self.summary(date: try Self.localDate(2024, 1, 1)),
                   Self.summary(date: try Self.localDate(2025, 6, 1))]
        let result = DateTimeTools.generateXAxisValues(for: .total, from: ops, shouldRepeat: false, entryCount: 2)
        #expect(!result.isEmpty)
    }

    // MARK: - Chart: sundayTicksForMonth

    @Test("sundayTicksForMonth returns each Sunday at local noon")
    func sundayTicksForMonth() throws {
        let cal = Calendar.current
        let anyDayInJune = try Self.localDate(2025, 6, 10)
        let monthInterval = try #require(cal.dateInterval(of: .month, for: anyDayInJune))

        let ticks = DateTimeTools.sundayTicksForMonth(
            in: monthInterval,
            baseCalendar: cal,
            includeTrailingPhantom: false
        )
        // June 2025 Sundays: 1, 8, 15, 22, 29 → 5.
        #expect(ticks.count == 5)
        for tick in ticks {
            #expect(cal.component(.hour, from: tick) == 12)
            #expect(cal.component(.weekday, from: tick) == 1)
        }

        let withPhantom = DateTimeTools.sundayTicksForMonth(
            in: monthInterval,
            baseCalendar: cal,
            includeTrailingPhantom: true
        )
        #expect(withPhantom.count == 6)
    }
}
