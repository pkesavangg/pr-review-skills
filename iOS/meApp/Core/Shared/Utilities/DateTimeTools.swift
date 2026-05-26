// DateTimeTools.swift

// This utility class intentionally aggregates all date/time formatting operations
// to provide a centralized, thread-safe API. Splitting would fragment related functionality
// and reduce discoverability of date/time utilities.

import Foundation

/// A utility class for date and time formatting, parsing, and timezone operations.
/// All methods are static and thread-safe.
final class DateTimeTools {
    // MARK: - Error Constants
    static let invalidString: String = "---"
    static let invalidInt: Int? = nil

    // MARK: - Cached Calendars (hot-path reuse)
    /// User's current calendar, cached once. Reading `Calendar.current` per call
    /// triggers `_LocaleICU.minimumDaysInFirstWeek.getter` — a leaf that surfaced in
    /// the dashboard scroll-hang traces (history doc §3.10 / §3.11). Reuse this
    /// instance for any read-only Calendar operation that doesn't need a custom
    /// timezone / locale / firstWeekday.
    static let currentCalendar: Calendar = Calendar.current

    /// Gregorian calendar aligned with the user's current timezone and locale.
    /// Used by `parseCalendarDate` and `sundayTicksForMonth` instead of constructing
    /// a fresh `Calendar(identifier: .gregorian)` per call (which triggers
    /// `Calendar.locale.setter`).
    static let gregorianCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        cal.locale = .current
        return cal
    }()

    /// UTC Gregorian calendar — used by `parseCalendarDate` for the UTC date-component
    /// extraction step.
    static let gregorianUTCCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC") ?? .current
        cal.locale = .current
        return cal
    }()

    /// Sunday-start Gregorian calendar aligned with the user's current locale and
    /// timezone. Used by `sundayTicksForMonth` instead of constructing per call.
    static let sundayStartCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        cal.locale = .current
        cal.firstWeekday = 1 // Sunday
        return cal
    }()

    // MARK: - DateFormatter Cache (Thread-Local)
    /// Returns a thread-local cached DateFormatter configured with the given format and optional timezone.
    /// DateFormatter is not thread-safe, so we cache per-thread to avoid contention and repeated allocations.
    private static func cachedFormatter(_ format: String, timeZone: TimeZone? = nil) -> DateFormatter {
        let tzKey = timeZone?.identifier ?? "__local__"
        let key = "DateTimeTools.formatter::" + format + "::" + tzKey
        if let existing = Thread.current.threadDictionary[key] as? DateFormatter {
            return existing
        }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.dateFormat = format
        if let tz: TimeZone = timeZone {
            df.timeZone = tz
        }
        Thread.current.threadDictionary[key] = df
        return df
    }

    /// Returns a cached DateFormatter with the specified format and en_US_POSIX locale.
    static func formatter(_ format: String) -> DateFormatter {
        return cachedFormatter(format, timeZone: nil)
    }

    /// Returns a new (non-cached) DateFormatter configured with format, locale and optional timezone.
    /// Use this when you need to mutate formatter properties (like timeZone) to avoid side-effects on cached instances.
    private static func ephemeralFormatter(_ format: String, timeZone: TimeZone? = nil) -> DateFormatter {
        // Use thread-local cache keyed by format and timezone instead of allocating new instances.
        return cachedFormatter(format, timeZone: timeZone)
    }

    // MARK: - Formatters
    /// Thread-local ISO8601 formatter with fractional seconds. Optional UTC configuration.
    public static func isoFormatter(useUTC: Bool? = nil) -> ISO8601DateFormatter {
        let scope = (useUTC == true) ? "utc" : "local"
        let key = "DateTimeTools.iso8601::" + scope
        if let existing = Thread.current.threadDictionary[key] as? ISO8601DateFormatter {
            return existing
        }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if useUTC == true {
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
        }
        Thread.current.threadDictionary[key] = formatter
        return formatter
    }

    // MARK: - Date Formatting

    /// Returns today's date formatted as 'LLL d, yyyy' (e.g., 'May 29, 2025').
    static func getTodayFormattedDate() -> String {
        // Get current date and format as 'LLL d, yyyy'
        let date = Date()
        return formatter("LLL d, yyyy").string(from: date)
    }

    /// Formats a date string (ISO or common formats) as 'LLL d, yyyy'. Returns empty string if invalid.
    static func getFormattedDate(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("LLL d, yyyy").string(from: date)
    }

    /// Formats a date string as 'MMM d' (e.g., 'May 29'). Returns empty string if invalid.
    static func getFormattedDay(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("MMM d").string(from: date)
    }

    /// Formats a date string as 'MMM d, h:mma' (e.g., 'May 29, 3:45PM'). Returns empty string if invalid.
    static func getFormattedDayWithTime(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("MMM d, h:mma").string(from: date)
    }

    /// Formats a time string as 'h:mm a' (e.g., '3:45 PM'). Returns empty string if invalid.
    static func getFormattedTime(_ timeString: String) -> String {
        guard let date = parse(timeString) else { return invalidString }
        return formatter("h:mm a").string(from: date)
    }

    /// Returns the month abbreviation (e.g., 'May') from a date string. Returns '---' if invalid.
    static func getMonth(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("MMM").string(from: date)
    }

    /// Returns the full month and year (e.g., 'May 2025') from a date string. Returns '----' if invalid.
    static func getMonthYear(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("MMMM yyyy").string(from: date)
    }

    /// Returns the full month, day, and year (e.g., 'May 29, 2025') from a date string. Returns '----' if invalid.
    static func getMonthDayYear(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("MMMM d, yyyy").string(from: date)
    }

    /// Returns the full month, day, and year (e.g., 'May 29, 2025') from a date string. Returns '----' if invalid.
    static func getMonthDayYearShort(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("MMM yyyy").string(from: date)
    }

    /// Returns the year (e.g., '2025') from a date string. Returns '----' if invalid.
    static func getYear(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy").string(from: date)
    }

    /// Returns the weekday (1 = Sunday, 7 = Saturday) from a date string. Returns nil if invalid.
    static func getDay(_ dateString: String) -> Int? {
        guard let date = parse(dateString) else { return invalidInt }
        return currentCalendar.component(.weekday, from: date)
    }

    static func getDateStringFromDate(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy-MM-dd").string(from: date)
    }

    /// Formats a UTC date string to 'yyyy-MM-dd' in the local timezone.
    /// - Parameter dateString: The UTC date string to format.
    /// - Returns: The formatted date string in local timezone.
    static func getLocalDateStringFromUTCDate(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return ephemeralFormatter("yyyy-MM-dd", timeZone: TimeZone.current).string(from: date)
    }

    /// Formats a UTC date string to 'yyyy-MM' in the local timezone.
    /// - Parameter dateString: The UTC date string to format.
    /// - Returns: The formatted month string in local timezone.
    static func getLocalMonthStringFromUTCDate(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return ephemeralFormatter("yyyy-MM", timeZone: TimeZone.current).string(from: date)
    }

    static func getDateFromDateString(_ dateString: String, format: String) -> Date {
        // Validate input
        guard !dateString.isEmpty else { return Date() }

        // Try to parse with the specific format first
        if let date = formatter(format).date(from: dateString) {
            return date
        }

        // Fallback to general parsing
        if let date = parse(dateString) {
            return date
        }

        // Last resort: return current date
        return Date()
    }

    static func getMonthStringFromDate(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy-MM").string(from: date)
    }

    // MARK: - ISO String

    /// Returns the current date and time as an ISO8601 string with fractional seconds.
    static func getCurrentDatetimeIsoString() -> String {
        return isoFormatter().string(from: Date())
    }

    /// Converts a date string to an ISO8601 string with fractional seconds. Returns empty string if invalid.
    static func getDatetimeIsoString(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return isoFormatter().string(from: date)
    }

    /// Returns the current date and time with timezone as 'yyyy-MM-dd HH:mm:ss.SSSSSSZ'.
    static func getCurrentTimeWithTimeZone() -> String {
        let date = Date()
        return formatter("yyyy-MM-dd HH:mm:ss.SSSSSSZ").string(from: date)
    }

    /// Returns an ISO8601 string for the date 'interval' days before the given start date (or today if nil), zeroed to midnight.
    static func getIntervalDatetimeIsoString(interval: Int, start: String? = nil) -> String {
        let startDate = start.flatMap { parse($0) } ?? Date()
        guard let intervalDate = currentCalendar.date(byAdding: .day, value: -interval, to: startDate) else {
            return invalidString
        }
        let zeroed = currentCalendar.date(bySettingHour: 0, minute: 0, second: 0, of: intervalDate) ?? intervalDate
        return isoFormatter().string(from: zeroed)
    }

    /// Formats a date string for use with Ionic date pickers as 'yyyy-MM-dd'T'HH:mm:ss'. Returns empty string if invalid.
    static func getDateStringFormattedForIonicDatePicker(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy-MM-dd'T'HH:mm:ss").string(from: date)
    }

    /// Returns the `Date` representing the max birthday allowed (13 years ago, zeroed to midnight).
    static func minAllowedBirthdayDate(yearsAgo: Int = 13) -> Date {
        let minDate = currentCalendar.date(byAdding: .year, value: -yearsAgo, to: Date()) ?? Date()
        let zeroed = currentCalendar.date(bySettingHour: 0, minute: 0, second: 0, of: minDate) ?? minDate
        return zeroed
    }

    /// Returns the minimum birthday offset (13 years ago, zeroed to midnight) formatted for Ionic date pickers as `'yyyy-MM-dd'T'HH:mm:ss'`.
    static func getMinBirthdayOffsetForDatePicker() -> String {
        let date = minAllowedBirthdayDate()
        return formatter("yyyy-MM-dd'T'HH:mm:ss").string(from: date)
    }

    /// Formats a birthday string as 'yyyy-MM-dd'T'HH:mm:ss'. Returns empty string if invalid.
    static func getBirthdayFormattedString(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy-MM-dd'T'HH:mm:ss").string(from: date)
    }

    // MARK: - Timezone

    /// Returns the user's current timezone identifier (e.g., 'America/Los_Angeles').
    static func getUserTimezone() -> String {
        return TimeZone.current.identifier
    }

    /// Returns the user's current timezone offset from GMT in minutes (positive east of GMT).
    static func getUserTimezoneOffset() -> Int {
        return TimeZone.current.secondsFromGMT() / 60
    }

    /// Returns the user's current timezone offset from GMT in minutes (negative for compatibility with some APIs).
    static func getTimeZoneInMinutes() -> Int {
        return TimeZone.current.secondsFromGMT() / 60
    }

    // MARK: - Helpers

    /// Parses a stored date-of-birth (or similar calendar-only) string into a local-midnight `Date`
    /// representing the same Y-M-D the server stored.
    ///
    /// The backend stores dob as UTC midnight ISO (e.g. `"1999-09-09T00:00:00.000Z"`) regardless of
    /// what timezone the client sends from — it strips the time and keeps the UTC date portion.
    /// Parsed as an instant and rendered in local time, that shifts one day earlier in any timezone
    /// west of UTC. This helper reads the Y-M-D in UTC and rebuilds at local midnight so the
    /// calendar day is preserved in any timezone.
    static func parseCalendarDate(_ dateString: String) -> Date? {
        if let date = formatter("yyyy-MM-dd").date(from: dateString) {
            return date
        }
        guard let instant = parse(dateString) else { return nil }
        // Reuse cached Gregorian calendars; previously this constructed two fresh
        // `Calendar(identifier: .gregorian)` per call, each triggering
        // `Calendar.locale.setter` that surfaces in scroll-hang stacks.
        let comps = gregorianUTCCalendar.dateComponents([.year, .month, .day], from: instant)
        return gregorianCalendar.date(from: DateComponents(year: comps.year, month: comps.month, day: comps.day))
    }

    /// Attempts to parse a date string using ISO8601 and several common formats.
    /// Returns a Date if successful, or nil if parsing fails.
    static func parse(_ dateString: String) -> Date? {
        // Try ISO8601 first
        if let date = isoFormatter().date(from: dateString) {
            return date
        }
        // Try common formats
        let formats = [
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        ]
        for format in formats {
            let df = formatter(format)
            if let date = df.date(from: dateString) {
                return date
            }
        }
        return nil
    }

    /// Returns the current timestamp in milliseconds since 1970.
    static func getCurrentTimestampMillis() -> Int64 {
        return Int64(Date().timeIntervalSince1970 * 1000)
    }

    /// Returns a timestamp (in ms) representing the date `days` ago from now.
    /// Useful for log cleanup cutoff comparison.
    static func getTimestampDaysAgo(_ days: Int) -> Int64 {
        let cutoffDate = currentCalendar.date(byAdding: .day, value: -days, to: Date()) ?? Date()
        return Int64(cutoffDate.timeIntervalSince1970 * 1000)
    }

    /// Formats a date to 'yyyy-MM-dd' in the local timezone.
    /// - Parameter date: The date to format.
    /// - Returns: The formatted date string.
    static func formatDateToYMD_Local(_ date: Date) -> String {
        return ephemeralFormatter("yyyy-MM-dd", timeZone: TimeZone.current).string(from: date)
    }

    static func getTimestamp(_ dateString: String) -> Int64 {
        guard let date = parse(dateString) else { return 0 }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    /// Combines a calendar `date` (uses Y-M-D) with a separate `time` (H:M:S) component into a single `Date`.
    /// - Parameters:
    ///   - date: Date whose Y-M-D components will be kept.
    ///   - time: Date whose H-M-S (and nanoseconds) components will be applied.
    /// - Returns: Combined `Date` in the user's current timezone (falls back to `date` on failure).
    static func combineDate(_ date: Date, withTime time: Date) -> Date {
        let calendar = currentCalendar
        var components = calendar.dateComponents([.year, .month, .day], from: date)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second, .nanosecond], from: time)
        components.hour = timeComponents.hour
        components.minute = timeComponents.minute
        components.second = timeComponents.second
        components.nanosecond = timeComponents.nanosecond
        components.timeZone = TimeZone.current
        return calendar.date(from: components) ?? date
    }

    /// Creates an ISO-8601 string like `2025-06-16T08:24:50.624Z` from a separate date and time selection.
    /// - Parameters:
    ///   - date: Date that provides the calendar day.
    ///   - time: Date that provides the time of day.
    ///   - useUTC: When `true`, result is in UTC with a `Z` suffix; otherwise it is in the user's timezone.
    /// - Returns: ISO-8601 string with fractional seconds.
    static func isoString(date: Date,
                          time: Date,
                          useUTC: Bool = true,
                          randomizeSubMinute: Bool = false) -> String {
        var combined = combineDate(date, withTime: time)

        if randomizeSubMinute {
            let calendar = currentCalendar
            var comps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: combined)
            // Use current time's milliseconds to ensure unique timestamps that preserve creation order
            let now = Date()
            let nowComps = calendar.dateComponents([.second, .nanosecond], from: now)
            comps.second = nowComps.second ?? 0
            comps.nanosecond = nowComps.nanosecond ?? 0
            comps.timeZone = TimeZone.current
            combined = calendar.date(from: comps) ?? combined
        }

        let formatter = isoFormatter(useUTC: useUTC)
        return formatter.string(from: combined)
    }

    // MARK: - Timezone Offset
    /// Returns the current user's timezone offset in minutes.
    static func getUTCOffset() -> Int {
        TimeZone.current.secondsFromGMT() / 60
    }

    // MARK: - Debug/Display Helpers
    /// Returns the current day and time formatted like "Jun 23, 9:20pm".
    /// Uses user's current locale but forces US_POSIX for predictable AM/PM replacement.
    static func getCurrentDayTimeShort() -> String {
        let date = Date()
        let df = formatter("MMM d, h:mma")
        var str = df.string(from: date)
        // Lower-case AM/PM only (keep month capitalised)
        str = str.replacingOccurrences(of: "AM", with: "am").replacingOccurrences(of: "PM", with: "pm")
        return str
    }

    /// Returns the current timezone string like "330 min\nAsia/Calcutta" (offset first, then identifier).
    static func getTimezoneOffsetString() -> String {
        let minutes = TimeZone.current.secondsFromGMT() / 60
        return "\(minutes) min \(TimeZone.current.identifier)"
    }

    // MARK: - Timestamp Conversion

    /// Converts a timestamp (in seconds since 1970) to a formatted date string like "June 10, 2019".
    /// - Parameter timestamp: The timestamp in seconds since 1970.
    /// - Returns: Formatted date string in "MMMM d, yyyy" format, or invalidString if conversion fails.
    static func getFormattedDateFromTimestamp(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp))
        return formatter("MMMM d, yyyy").string(from: date)
    }

    /// Converts a timestamp (in seconds since 1970) to a formatted date string like "May 29, 2025".
    /// - Parameter timestamp: The timestamp in seconds since 1970.
    /// - Returns: Formatted date string in "LLL d, yyyy" format, or invalidString if conversion fails.
    static func getShortFormattedDateFromTimestamp(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp))
        return formatter("LLL d, yyyy").string(from: date)
    }

    // MARK: - X-Axis Label and Domain Calculation Methods

    /// Generates X-axis values for different time periods
    static func generateXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary], shouldRepeat: Bool, entryCount: Int) -> [Date] {
        let allDates = operations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }

        switch period {
        case .week:
            return generateWeeklyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        case .month:
            return generateMonthlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        case .year:
            return generateYearlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        case .total:
            return generateTotalXAxis(minDate: minDate, maxDate: maxDate, operations: operations, shouldRepeat: shouldRepeat, entryCount: entryCount)
        }
    }

    /// Formats X-axis labels for different time periods.
    /// Called per Mark per chart body recompute on the dashboard hot path —
    /// see `BaseSectionViewModel.formatXAxisLabel` and history doc §3.11.
    /// Reuse `currentCalendar` to avoid `_LocaleICU` reads per call.
    static func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        let calendar = currentCalendar

        switch period {
        case .week:
            return WeekDay.abbreviation(for: calendar.component(.weekday, from: date))
        case .month:
            return "\(calendar.component(.day, from: date))"
        case .year:
            return Month.initial(for: calendar.component(.month, from: date))
        case .total:
            if areEntriesInSameEra(operations) {
                return Month.initial(for: calendar.component(.month, from: date))
            } else {
                return "\(calendar.component(.year, from: date))"
            }
        }
    }

    /// Determines if X-axis labels should repeat based on period and entry count
    static func shouldRepeatXAxisLabels(for period: TimePeriod, entryCount: Int) -> Bool {
        switch period {
        case .week:
            return entryCount >= DashboardConstants.Thresholds.weekRepeatThreshold
        case .month:
            return entryCount >= DashboardConstants.Thresholds.monthRepeatThreshold
        case .year, .total:
            return entryCount >= DashboardConstants.Thresholds.yearRepeatThreshold
        }
    }

    /// Returns Sunday ticks for a calendar month interval at local noon.
    /// - Parameters:
    ///   - monthInterval: Interval representing the target month (end is exclusive).
    ///   - baseCalendar: Calendar providing timezone/locale context.
    ///   - includeTrailingPhantom: Appends one extra Sunday tick after the last real Sunday.
    static func sundayTicksForMonth(
        in monthInterval: DateInterval,
        baseCalendar: Calendar,
        includeTrailingPhantom: Bool
    ) -> [Date] {
        // Reuse cached Sunday-start Gregorian calendar. `baseCalendar`'s timezone
        // and locale come from `Calendar.current` upstream, so the cached value
        // is equivalent. Previously this constructed a fresh calendar per call.
        let cal = sundayStartCalendar

        let monthStart = monthInterval.start
        let monthEnd = monthInterval.end
        let beforeMonthStart = monthStart.addingTimeInterval(-1)

        guard let firstSunday = cal.nextDate(
            after: beforeMonthStart,
            matching: DateComponents(weekday: 1),
            matchingPolicy: .nextTime,
            direction: .forward
        ) else {
            return []
        }

        var ticks: [Date] = []
        var sunday = firstSunday
        while sunday < monthEnd {
            let sundayNoon = cal.date(bySettingHour: 12, minute: 0, second: 0, of: sunday) ?? sunday
            ticks.append(sundayNoon)
            guard let nextSunday = cal.date(byAdding: .weekOfYear, value: 1, to: sunday) else { break }
            sunday = nextSunday
        }

        if includeTrailingPhantom,
           let last = ticks.last,
           let nextSunday = cal.date(byAdding: .weekOfYear, value: 1, to: last) {
            let phantomNoon = cal.date(bySettingHour: 12, minute: 0, second: 0, of: nextSunday) ?? nextSunday
            ticks.append(phantomNoon)
        }

        return ticks
    }

    /// Checks if entries are in the same era (same year)
    static func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }
        let calendar = currentCalendar

        // Validate that all summaries have valid dates
        let validSummaries = summaries.filter { summary in
            // Ensure the date is not in the distant past or future (basic validation)
            let year = calendar.component(.year, from: summary.date)
            return year >= 1900 && year <= 2100
        }

        guard !validSummaries.isEmpty else { return true }

        let years = Set(validSummaries.map { calendar.component(.year, from: $0.date) })
        return years.count == 1
    }

    /// Calculates visible domain length for different time periods
    static func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:
            return DashboardConstants.TimeInterval.week
        case .month:
            return DashboardConstants.TimeInterval.month
        case .year:
            return DashboardConstants.TimeInterval.year
        case .total:
            return DashboardConstants.TimeInterval.year
        }
    }

    // MARK: - X-Axis Generation Methods

    private static func generateWeeklyXAxis(minDate: Date, maxDate: Date, shouldRepeat: Bool, entryCount: Int) -> [Date] {
        let calendar = currentCalendar
        var dates: [Date] = []

        if !shouldRepeat {
            // Few entries: show labels once
            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            for dayOffset in 0..<7 {
                if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekStart) {
                    dates.append(dayDate)
                }
            }
        } else {
            // Many entries: repeat labels throughout scroll view
            let totalWeeks = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.calendarWeek)))
            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            let bufferWeeks = 2

            for weekOffset in -bufferWeeks..<(totalWeeks + bufferWeeks) {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                    for dayOffset in 0..<7 {
                        if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekDate) {
                            dates.append(dayDate)
                        }
                    }
                }
            }
        }

        return dates
    }

    private static func generateMonthlyXAxis(minDate: Date, maxDate: Date, shouldRepeat: Bool, entryCount: Int) -> [Date] {
        let calendar = currentCalendar
        var dates: [Date] = []

        if !shouldRepeat {
            // Few entries: show labels once
            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            for weekOffset in 0..<5 {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthStart) {
                    dates.append(weekDate)
                }
            }
        } else {
            // Many entries: repeat labels throughout scroll view
            let totalMonths = max(6, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.month)))
            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            let bufferMonths = 2

            for monthOffset in -bufferMonths..<(totalMonths + bufferMonths) {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                    for weekOffset in 0..<5 {
                        if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthDate) {
                            dates.append(weekDate)
                        }
                    }
                }
            }
        }

        return dates
    }

    private static func generateYearlyXAxis(minDate: Date, maxDate: Date, shouldRepeat: Bool, entryCount: Int) -> [Date] {
        let calendar = currentCalendar
        var dates: [Date] = []

        if !shouldRepeat {
            // Few entries: show labels once
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            for monthOffset in 0..<12 {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                    dates.append(monthDate)
                }
            }
        } else {
            // Many entries: repeat labels throughout scroll view
            let totalYears = max(3, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.year)))
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            let bufferYears = 1

            for yearOffset in -bufferYears..<(totalYears + bufferYears) {
                if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                    for monthOffset in 0..<12 {
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                            dates.append(monthDate)
                        }
                    }
                }
            }
        }

        return dates
    }

    private static func generateTotalXAxis(minDate: Date, maxDate: Date, operations: [BathScaleWeightSummary], shouldRepeat: Bool, entryCount: Int) -> [Date] {
        let calendar = currentCalendar

        if areEntriesInSameEra(operations) {
            // For same era, treat like year view
            return generateYearlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        } else {
            // For multiple years, use quarterly intervals
            let totalQuarters = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.quarter)))
            let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let bufferQuarters = 2
            var dates: [Date] = []

            for quarterOffset in -bufferQuarters..<(totalQuarters + bufferQuarters) {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                    dates.append(quarterDate)
                }
            }

            return dates
        }
    }
}
