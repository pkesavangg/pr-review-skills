//
//  HistoryListStrings.swift
//  meApp
//
//  Created by Generated on 18/06/25.
//

import Foundation

/// Centralised localisation keys/strings for History List screen.
enum HistoryListStrings {
    static let title = "History"
    static let entries = "entries"
    static let average = "average"
    static let change = "change"
    static let emptyState = "No history data yet"
    static let retry = "Retry"

    // Blood Pressure
    static let avgPressure = "avg pressure"
    static let avgPulse = "avg pulse"
    static let mmhg = "mmhg"
    static let pulse = "pulse"
    static let systolic = "systolic"
    static let diastolic = "diastolic"
    static let notes = "Notes"
    static let noNotesPlaceholder = "no notes yet — tap plus icon to add one."
    static let downloadBPHistory = "Download BP History"
    static let downloadBabyHistory = "Download Baby History"

    // Baby
    static let weight = "weight"
    static let length = "length"
    static let percentile = "percent"
    static let week = "week"
    static let oz = "oz"
    static let kg = "kg"
    static let lb = "lb"
    static let cm = "cm"
    static let inUnit = "in"
    static let th = "th"
    static let pounds = "pounds"
    static let ounces = "ounces"
    static let inches = "inches"

    // Edit sheet
    static let editReading = "Edit Reading"
    static let date = "DATE"
    static let addNotesPlaceholder = "Add notes…"
    static let diastolicMmhg = "DIASTOLIC (mmhg)"
    static let measurement = "MEASUREMENT"

    // Delete / Undo
    static let undo = "UNDO"
    static let readingDeleted = "Reading deleted."
    static let readingRestored = "Reading restored."
    static let couldntDelete = "Couldn't delete!"
    static let tryAgain = "TRY AGAIN"

    // MARK: - Accessibility (VoiceOver) — spoken text only, not shown on screen
    static let accDeleteEntryLabel = "Delete entry"
    static let accMonthRowHint = "Double tap to view entries"
    static let accDayRowHint = "Double tap to view entries"
    static let accEntryExpandHint = "Double tap to expand metrics"
    static let accEntryCollapseHint = "Double tap to collapse metrics"
    static let accAddNoteLabel = "Add note"
    static let accEditNoteLabel = "Edit note"
    static let accBirthdayBalloonLabel = "Birthday"
}
