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
    static let percentSymbol = "%"

    // MARK: - Labels
    static let weekAverage = "week average"
    static let weight = "Weight"
    static let height = "Height"
    static let growthPercentilesTitle = "CDC Growth Percentiles"
    static let growthPercentilesHeading = "Understanding Growth Percentiles"
    static let growthPercentilesDescription =
        """
        CDC charts show how a child’s height and weight compare to others the same age. \
        A percentile means what percentage of children measure below that number.
        """
    static let noHeightData = "No height readings yet"

    static func babyWeightLabel(name: String) -> String {
        "\(name.lowercased())'s weight"
    }

    static func babyHeightLabel(name: String) -> String {
        "\(name.lowercased())'s height"
    }

    // MARK: - Growth chart (Smart Baby / WHO reference)
    /// Citation for percentile curves and calculations that follow WHO Child Growth Standards.
    static let growthChartAttribution =
        "Growth percentiles use WHO Child Growth Standards (\(BabyPercentileGrowthReference.whoChildGrowthStandardsYear))."

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
