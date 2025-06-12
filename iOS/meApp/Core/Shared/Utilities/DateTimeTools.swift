// DateTimeTools.swift

import Foundation

/// A utility class for date and time formatting, parsing, and timezone operations.
/// All methods are static and thread-safe.
final class DateTimeTools {
    // MARK: - Error Constants
    static let invalidString: String = "---"
    static let invalidInt: Int? = nil
    
    // MARK: - DateFormatter Cache
    private static var formatterCache: [String: DateFormatter] = [:]
    private static let formatterLock = NSLock()
    
    /// Returns a cached DateFormatter with the specified format and en_US_POSIX locale.
    private static func formatter(_ format: String) -> DateFormatter {
        formatterLock.lock()
        defer { formatterLock.unlock() }
        if let cached = formatterCache[format] {
            return cached
        }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.dateFormat = format
        formatterCache[format] = df
        return df
    }
    
    // MARK: - Formatters
    /// Shared ISO8601 formatter with fractional seconds for parsing and formatting ISO strings.
    private static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    
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
    
    /// Returns the year (e.g., '2025') from a date string. Returns '----' if invalid.
    static func getYear(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy").string(from: date)
    }
    
    /// Returns the weekday (1 = Sunday, 7 = Saturday) from a date string. Returns nil if invalid.
    static func getDay(_ dateString: String) -> Int? {
        guard let date = parse(dateString) else { return invalidInt }
        return Calendar.current.component(.weekday, from: date)
    }
    
    // MARK: - ISO String
    
    /// Returns the current date and time as an ISO8601 string with fractional seconds.
    static func getCurrentDatetimeIsoString() -> String {
        return isoFormatter.string(from: Date())
    }
    
    /// Converts a date string to an ISO8601 string with fractional seconds. Returns empty string if invalid.
    static func getDatetimeIsoString(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return isoFormatter.string(from: date)
    }
    
    /// Returns the current date and time with timezone as 'yyyy-MM-dd HH:mm:ss.SSSSSSZ'.
    static func getCurrentTimeWithTimeZone() -> String {
        let date = Date()
        return formatter("yyyy-MM-dd HH:mm:ss.SSSSSSZ").string(from: date)
    }
    
    /// Returns an ISO8601 string for the date 'interval' days before the given start date (or today if nil), zeroed to midnight.
    static func getIntervalDatetimeIsoString(interval: Int, start: String? = nil) -> String {
        let startDate = start.flatMap { parse($0) } ?? Date()
        guard let intervalDate = Calendar.current.date(byAdding: .day, value: -interval, to: startDate) else {
            return invalidString
        }
        let zeroed = Calendar.current.date(bySettingHour: 0, minute: 0, second: 0, of: intervalDate) ?? intervalDate
        return isoFormatter.string(from: zeroed)
    }
    
    /// Formats a date string for use with Ionic date pickers as 'yyyy-MM-dd'T'HH:mm:ss'. Returns empty string if invalid.
    static func getDateStringFormattedForIonicDatePicker(_ dateString: String) -> String {
        guard let date = parse(dateString) else { return invalidString }
        return formatter("yyyy-MM-dd'T'HH:mm:ss").string(from: date)
    }
    
    /// Returns the `Date` representing the max birthday allowed (13 years ago, zeroed to midnight).
    static func minAllowedBirthdayDate(yearsAgo: Int = 13) -> Date {
        let minDate = Calendar.current.date(byAdding: .year, value: -yearsAgo, to: Date()) ?? Date()
        let zeroed = Calendar.current.date(bySettingHour: 0, minute: 0, second: 0, of: minDate) ?? minDate
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
        return -TimeZone.current.secondsFromGMT() / 60
    }
    
    // MARK: - Helpers
    
    /// Attempts to parse a date string using ISO8601 and several common formats.
    /// Returns a Date if successful, or nil if parsing fails.
    static func parse(_ dateString: String) -> Date? {
        // Try ISO8601 first
        if let date = isoFormatter.date(from: dateString) {
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
        let cutoffDate = Calendar.current.date(byAdding: .day, value: -days, to: Date()) ?? Date()
        return Int64(cutoffDate.timeIntervalSince1970 * 1000)
    }
}
