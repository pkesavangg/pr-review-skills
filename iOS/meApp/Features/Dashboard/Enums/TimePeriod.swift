//
//  TimePeriod.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//

import Foundation

enum TimePeriod: String, CaseIterable, Identifiable {
    case week
    case month
    case year
    case total

    var id: String { self.rawValue }
    var displayName: String { self.rawValue }

    /// Capitalized title used in user-facing UI (e.g., the Settings picker).
    var title: String { self.rawValue.capitalized }

    /// Returns the granularity level (lower = more detailed)
    /// week = 1, month = 2, year = 3, total = 4
    private var granularityLevel: Int {
        switch self {
        case .week: return 1
        case .month: return 2
        case .year: return 3
        case .total: return 4
        }
    }

    /// Returns true if this period shows more detail than the other period.
    /// For example, week.isMoreDetailedThan(month) returns true.
    /// Intended to support determining zoom direction and anchor logic when switching
    /// between periods in dashboard charts. This helper is kept for this purpose even
    /// if not currently referenced in all call sites.
    func isMoreDetailedThan(_ other: TimePeriod) -> Bool {
        return self.granularityLevel < other.granularityLevel
    }
}

enum WeekDay: Int, CaseIterable {
    case sunday = 1, monday, tuesday, wednesday, thursday, friday, saturday
    var abbreviation: String {
        switch self {
        case .sunday: "Sun"
        case .monday: "Mon"
        case .tuesday: "Tue"
        case .wednesday: "Wed"
        case .thursday: "Thu"
        case .friday: "Fri"
        case .saturday: "Sat"
        }
    }
    static func abbreviation(for weekday: Int) -> String {
        WeekDay(rawValue: weekday)?.abbreviation ?? ""
    }
}

enum Month: Int, CaseIterable {
    case january = 1, february, march, april, may, june, july, august, september, october, november, december
    var initial: String {
        switch self {
        case .january: "J"
        case .february: "F"
        case .march: "M"
        case .april: "A"
        case .may: "M"
        case .june: "J"
        case .july: "J"
        case .august: "A"
        case .september: "S"
        case .october: "O"
        case .november: "N"
        case .december: "D"
        }
    }
    static func initial(for month: Int) -> String {
        Month(rawValue: month)?.initial ?? ""
    }
}

// MARK: - Supporting Types
enum PeriodType {
    case day
    case month
}
