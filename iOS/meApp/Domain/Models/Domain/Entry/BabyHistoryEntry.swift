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
    /// Weight in pounds (whole part) — used for imperial display
    let weightLbs: Int
    /// Weight in ounces (fractional part) — used for imperial display
    let weightOz: Double
    /// Weight in kilograms — used for metric display
    let weightKg: Double
    /// Weight in decimal pounds — used for lb display
    let weightLb: Double
    /// Length in inches
    let lengthInches: Double
    /// Length in centimeters — used for metric display
    let lengthCm: Double
    /// Growth percentile
    let percentile: Int
    /// Optional notes
    let notes: String?
    /// Pre-formatted weight string based on account unit preference (e.g. "8 lbs 12.5 oz" or "3.97 kg")
    let weightDisplay: String
    /// Pre-formatted length string based on account unit preference (e.g. "18 in" or "45.7 cm")
    let lengthDisplay: String
}
