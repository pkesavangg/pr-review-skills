//
//  BpmDashboardStrings.swift
//  meApp
//

import Foundation

/// Centralised strings for the BPM dashboard feature.
enum BpmDashboardStrings {
    // MARK: - Units
    static let mmhg = "mmhg"
    static let pulse = "pulse"
    static let bpm = "bpm"

    // MARK: - Labels
    static let bloodPressure = "Blood Pressure"
    static let systolic = "Systolic"
    static let diastolic = "Diastolic"
    static let meanArterial = "Mean Arterial"

    // MARK: - Cards
    static let threeEntryAverage = "three entry average"
    static let twoEntryAverage = "two entry average"
    static let lastReading = "last reading"
    static let threeReadingAverageTitle = "Three Reading Average"
    static let ahaRating = "AHA Rating"

    // MARK: - AHA Rating Sheet
    static let ahaRatings = "AHA Ratings"
    static let bloodPressureLevelColors = "Blood Pressure Level Colors"
    static let colorChartDescription = "Colors show where a reading falls — from Normal to Hypertensive Crisis — based on American Heart Association guidelines."

    // MARK: - Empty State
    static let noReadingsTitle = "No blood pressure readings yet"
    static let noReadingsSubtitle = "Add your first reading to see your blood pressure trends."
    static let addReading = "Add Reading"
    static let noReadingsYet = "No readings yet"
    static let tapToAddFirstReading = "Tap to add your first reading"
}
