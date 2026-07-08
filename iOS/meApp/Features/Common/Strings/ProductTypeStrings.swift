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

    // MARK: - History empty states (per product & state) — MOB-1220
    /// Copy for the History-tab empty states shown once a profile exists but the user has no
    /// device paired ("no device") or a device is paired but no entries were recorded ("no measurement").
    /// The primary CTA is ADD DEVICE while no device is paired and flips to LOG MANUALLY once one is.
    enum EmptyState {
        // Shared CTAs
        static let addDevice = "ADD DEVICE"
        static let logManually = "LOG MANUALLY"

        // Weight scale
        static let weightNoDeviceTitle = "No scale connected"
        static let weightNoDeviceDescription = "Add a weight scale to start monitoring your weight."
        static let weightNoEntriesTitle = "No measurements yet"
        static let weightNoEntriesDescription = "Take a reading using your scale or log a measurement manually."

        // Blood pressure monitor
        static let bpNoDeviceTitle = "No monitor connected"
        static let bpNoDeviceDescription = "Add a blood pressure monitor to start tracking your readings."
        static let bpNoEntriesTitle = "No readings yet"
        static let bpNoEntriesDescription = "Take a reading using your monitor or log one manually."

        // Baby scale (profile already exists; the no-profile state lives in `BabyEmptyState`)
        static let babyNoDeviceTitle = "No baby scale paired yet"
        static let babyNoDeviceDescription = "Add a baby scale to start tracking, or log a measurement manually."
        static let babyNoEntriesTitle = "No measurements yet"
        static let babyNoEntriesDescription = "Weigh your baby using your scale or log a measurement manually."
    }
}
