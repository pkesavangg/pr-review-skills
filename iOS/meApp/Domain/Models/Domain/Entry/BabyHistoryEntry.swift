//
//  BabyHistoryEntry.swift
//  meApp
//

import Foundation

/// Represents a single baby measurement entry in history.
struct BabyHistoryEntry: Identifiable, Equatable {
    let id: UUID
    /// ISO8601 timestamp string
    let entryTimestamp: String
    /// Weight in pounds (whole part)
    let weightLbs: Int
    /// Weight in ounces (fractional part)
    let weightOz: Double
    /// Length in inches
    let lengthInches: Double
    /// Growth percentile
    let percentile: Int
    /// Optional notes
    let notes: String?
}
