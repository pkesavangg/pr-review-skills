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
    static let whyWeTakeAnAverage = "Why We Take an Average"
    static let averageExplanation = "Blood pressure changes throughout the day. Averaging three readings gives a more accurate result."
    static let lastThreeReadings = "Last 3 readings"
    static let noEntries = "no entries"

    // MARK: - AHA Rating Sheet
    static let ahaRatings = "AHA Ratings"
    static let bloodPressureLevelColors = "Blood Pressure Level Colors"
    static let colorChartDescription =
        "Colors show where a reading falls — from Normal to Hypertensive Crisis — based on American Heart Association guidelines."

    // MARK: - Zero-value placeholders (shown when no entries exist)
    static let bpSystolicZeroPlaceholder = "000"
    static let bpDiastolicZeroPlaceholder = "00"
    static let bpPulseZeroPlaceholder = "00"

    // MARK: - Accessibility
    static let openAhaRatings = "Open AHA ratings"
    static let noBloodPressureData = "No blood pressure data"
    static let loadingBloodPressure = "Loading blood pressure"
    static let bpPlaceholder = "--/--"
    static let systolicRangePrefix = "Systolic: "
    static let diastolicRangePrefix = "\nDiastolic: "

    // MARK: - AHA Classification Labels
    static let ahaNormal = "Normal"
    static let ahaElevated = "Elevated"
    static let ahaHypertensionStage1 = "Hypertension Stage 1"
    static let ahaHypertensionStage2 = "Hypertension Stage 2"
    static let ahaHypertensiveCrisis = "Hypertensive Crisis"

    // MARK: - AHA Systolic Ranges
    static let systolicNormal = "Less than 120"
    static let systolicElevated = "120-129"
    static let systolicStage1 = "130-139"
    static let systolicStage2 = "140 or higher"
    static let systolicCrisis = "Higher than 180"

    // MARK: - AHA Diastolic Ranges
    static let diastolicNormal = "Less than 80"
    static let diastolicElevated = "Less than 80"
    static let diastolicStage1 = "80-89"
    static let diastolicStage2 = "90 or higher"
    static let diastolicCrisis = "Higher than 120"

    // MARK: - Empty State
    static let noReadingsTitle = "No blood pressure readings yet"
    static let noReadingsSubtitle = "Add your first reading to see your blood pressure trends."
    static let addReading = "Add Reading"
    static let noReadingsYet = "No readings yet"
    static let tapToAddFirstReading = "Tap to add your first reading"

    // MARK: - Accessibility (dynamic)
    static let bloodPressureSnapshotNoReadings = "Blood pressure snapshot, no readings yet"

    static func bpReadingAccessibility(systolic: Int, diastolic: Int, pulse: Int, label: String) -> String {
        "Blood pressure \(systolic) over \(diastolic), pulse \(pulse), \(label)"
    }

    static func bpReadingCardAccessibility(systolic: Int, diastolic: Int, pulse: Int, label: String, date: String) -> String {
        "\(systolic) over \(diastolic), pulse \(pulse), \(label), \(date)"
    }

    static func bpAverageCardAccessibility(label: String, systolic: Int, diastolic: Int, pulse: Int) -> String {
        "\(label). Systolic \(systolic), Diastolic \(diastolic), Pulse \(pulse)"
    }

    static func bpReadingMmhgAccessibility(systolic: Int, diastolic: Int, pulse: Int, date: String) -> String {
        "\(systolic) over \(diastolic) millimeters mercury, pulse \(pulse), \(date)"
    }

    static func bpSnapshotAccessibility(systolic: Int, diastolic: Int, pulse: Int) -> String {
        "Blood pressure snapshot, \(systolic) over \(diastolic), pulse \(pulse)"
    }

    static func ahaRatingAccessibility(label: String) -> String {
        "AHA rating: \(label). Tap for details."
    }
}
