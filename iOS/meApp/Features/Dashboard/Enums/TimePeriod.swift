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
