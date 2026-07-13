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
    /// `true` when any day in this week falls on the baby's birthday (MOB-1164) —
    /// drives the birthday balloon shown before the week label.
    var containsBirthday: Bool {
        days.contains { $0.isBirthday }
    }
}

/// Represents a single day summary within baby history.
struct BabyHistoryDay: Identifiable, Equatable, Hashable {
    /// Unique identifier — date string in "yyyy-MM-dd" format
    let id: String
    /// Number of entries on this day
    let entryCount: Int
    /// Average weight in pounds (whole part) — used for imperial display
    let weightLbs: Int
    /// Average weight in ounces (fractional part) — used for imperial display
    let weightOz: Double
    /// Average weight in kilograms — used for metric display
    let weightKg: Double
    /// Average weight in decimal pounds — used for lb display
    let weightLb: Double
    /// Average length in inches
    let lengthInches: Double
    /// Average length in centimeters — used for metric display
    let lengthCm: Double
    /// Average growth percentile
    let percentile: Int
    /// Pre-formatted weight string based on account unit preference
    let weightDisplay: String
    /// Pre-formatted length string based on account unit preference
    let lengthDisplay: String
    /// `true` when this day matches the baby's birthday (MOB-1164) — drives the
    /// birthday balloon shown before the date in the list row and detail header.
    /// Defaults to `false` when no birthday is set on the profile.
    var isBirthday: Bool = false
}
