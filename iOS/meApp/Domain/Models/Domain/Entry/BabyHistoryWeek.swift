//
//  BabyHistoryWeek.swift
//  meApp
//

import Foundation

/// Represents a weekly group of baby history day summaries.
struct BabyHistoryWeek: Identifiable, Equatable, Hashable {
    /// Unique identifier, e.g. "week-4"
    let id: String
    /// Week number label (e.g. 4)
    let weekNumber: Int
    /// Day summaries within this week
    let days: [BabyHistoryDay]
}

/// Represents a single day summary within baby history.
struct BabyHistoryDay: Identifiable, Equatable, Hashable {
    /// Unique identifier — date string in "yyyy-MM-dd" format
    let id: String
    /// Number of entries on this day
    let entryCount: Int
    /// Average weight in pounds (whole part)
    let weightLbs: Int
    /// Average weight in ounces (fractional part)
    let weightOz: Double
    /// Average length in inches
    let lengthInches: Double
    /// Average growth percentile
    let percentile: Int
}
