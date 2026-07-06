//
//  ManualEntryStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//

import Foundation

struct ManualEntryStrings {
    static let title = "Manual Entry"
    static let bodyMetrics = "Body Metrics"
    static let optional = "optional"
    static let systolic = "systolic"
    static let diastolic = "diastolic"
    static let pulse = "pulse"
    static let notes = "notes"

    static let pounds = "weight (lb)"
    static let ounces = "weight (oz)"
    static let inches = "length (in)"
    static let kg = "weight (kg)"
    static let lb = "weight (lb)"
    static let cm = "length (cm)"

    // Baby validation error messages
    static let required = "Required."
    static let invalidWeight = "Please enter a valid weight."
    static let invalidLength = "Please enter a valid length."

    // BP validation error messages
    static let maxLimit = "This value cannot be over 500."
    static let systolicReversed = "Systolic should be higher than diastolic"
    static let diastolicReversed = "Diastolic should be lower than systolic"
    static func typicalRange(_ min: Int, _ max: Int) -> String {
        "This value is outside the typical range of \(min) to \(max)."
    }

    // MARK: - Accessibility (VoiceOver) — spoken text only, not shown on screen
    static let accDateHint = "Opens date picker"
    static let accTimeHint = "Opens time picker"
    static let accBodyMetricsHeader = "Body metrics, optional"
    static let accBodyMetricsExpandHint = "Expands body metrics section"
    static let accBodyMetricsCollapseHint = "Collapses body metrics section"
}
