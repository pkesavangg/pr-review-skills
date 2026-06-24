//
//  ProductTypeStrings.swift
//  meApp
//

import Foundation

/// Centralised strings for the product type selector.
enum ProductTypeStrings {
    // MARK: - Dropdown item names
    static let myWeight = "My Weight"
    static let myBloodPressure = "My Blood Pressure"
    static let babyScale = "Baby Scale"

    // MARK: - History screen titles
    static let weightHistory = "Weight History"
    static let bloodPressure = "Blood Pressure"

    // MARK: - Manual Entry screen titles
    static let weightEntry = "Weight Entry"
    static let bpEntry = "BP Entry"

    // MARK: - Dashboard screen titles
    static let myBP = "My BP"

    // MARK: - Sheet titles
    static let myHistory = "My History"
    static let manualEntry = "Manual Entry"
    static let myDashboard = "My Dashboard"

    // MARK: - Baby empty state (no baby profile yet)
    /// Shown on the Manual Entry and History tabs when a baby scale exists but no baby profile has been added.
    enum BabyEmptyState {
        static let title = "No babies added yet"
        static let entryDescription = "Add a baby profile to log measurements manually."
        static let historyDescription = "Add a baby profile to view their measurement history."
        static let addABaby = "ADD A BABY"
    }
}
