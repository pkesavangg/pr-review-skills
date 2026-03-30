//
//  BabyDashboardStrings.swift
//  meApp
//

import Foundation

enum BabyDashboardStrings {
    // MARK: - Units
    static let lbs = "lbs"
    static let oz = "oz"
    static let inches = "inches"

    // MARK: - Labels
    static let weekAverage = "week average"
    static let weight = "Weight"
    static let height = "Height"

    static func babyWeightLabel(name: String) -> String {
        "\(name.lowercased())'s weight"
    }

    // MARK: - Empty State
    static let noReadingsYet = "No readings yet"

    // MARK: - Accessibility
    static let babySnapshotNoReadings = "Baby snapshot, no readings yet"

    static func babyWeightSnapshotAccessibility(name: String, lbs: String, oz: String) -> String {
        "\(name) snapshot, weight \(lbs) pounds \(oz) ounces"
    }

    static func babyHeightSnapshotAccessibility(name: String, inches: String) -> String {
        "\(name) snapshot, height \(inches) inches"
    }
}
