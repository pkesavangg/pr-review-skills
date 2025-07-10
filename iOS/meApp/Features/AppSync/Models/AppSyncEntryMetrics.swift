//
//  AppSyncEntryMetrics.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//


import SwiftUI

/// Encapsulates body-composition metrics coming from an AppSync scan.
/// `weight` is required, all other properties are optional. When optional
/// metrics are `nil` the UI will show "--".
struct AppSyncEntryMetrics {
    // MARK: – Raw stored values (tenths-based)
    /// Weight in *tenths of lbs* exactly as stored in DB. Always available for a valid scan.
    let storedWeight: Int
    /// BMI in *tenths* – optional because the scale might not return it.
    let storedBMI: Int?
    /// Body-fat percentage in *tenths*.
    let storedBodyFat: Int?
    /// Water-weight percentage in *tenths*.
    let storedWaterWeight: Int?
    /// Muscle-mass percentage in *tenths*.
    let storedMuscleMass: Int?
    /// Whether the preferred display unit is metric (`kg`) instead of imperial (`lbs`).
    let isMetric: Bool
    /// Raw weight reported by AppSync in **kilograms**. Helpful when editing or re-converting units.
    let rawDisplayWeightKg: Double?

    /// Formatted display strings (e.g. "72.4 kg", "15.2%", "--")
    let weight: String?
    let bodyFat: String?
    let muscleMass: String?
    let waterWeight: String?
    let bmi: String?

    /// Primary initializer that accepts raw stored values (tenths-based ints coming from the scale),
    /// keeps those for persistence, and simultaneously prepares user-friendly display strings for UI.
    /// - Parameters:
    ///   - storedWeight: Weight in *tenths of lbs* as stored in DB.
    ///   - storedBMI: BMI in *tenths* (e.g. 237 ⇒ 23.7).
    ///   - storedBodyFat: Body-fat % in *tenths*.
    ///   - storedWaterWeight: Water weight % in *tenths*.
    ///   - storedMuscleMass: Muscle mass % in *tenths*.
    ///   - isMetric: `true` to show kg, `false` to show lbs.
    ///   - rawDisplayWeightKg: Original weight in **kg** as reported by AppSync (optional).
    init(
        storedWeight: Int,
        storedBMI: Int? = nil,
        storedBodyFat: Int? = nil,
        storedWaterWeight: Int? = nil,
        storedMuscleMass: Int? = nil,
        isMetric: Bool,
        rawDisplayWeightKg: Double? = nil
    ) {
        // Persist raw values
        self.storedWeight = storedWeight
        self.storedBMI = storedBMI
        self.storedBodyFat = storedBodyFat
        self.storedWaterWeight = storedWaterWeight
        self.storedMuscleMass = storedMuscleMass
        self.isMetric = isMetric
        self.rawDisplayWeightKg = rawDisplayWeightKg

        // --- Display string computation ---
        let unit = isMetric ? "kg" : "lbs"
        let displayWeight = ConversionTools.convertStoredToDisplay(storedWeight, isMetric: isMetric)
        self.weight = String(format: "%.1f %@", displayWeight, unit)

        // Helper to format %-based metrics coming in as tenths
        func percentString(_ value: Int?) -> String? {
            guard let value else { return nil }
            return String(format: "%.1f%%", Double(value) / 10.0)
        }

        self.bmi = storedBMI.map { String(format: "%.1f", Double($0) / 10.0) }
        self.bodyFat = percentString(storedBodyFat)
        self.waterWeight = percentString(storedWaterWeight)
        self.muscleMass = percentString(storedMuscleMass)
    }
}