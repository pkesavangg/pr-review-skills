//
//  WeightOnlyModeStrings.swift
//  meApp
//
//  Created by AI Assistant on 14/08/25.
//

import Foundation

/// String constants for weight-only mode indicator and alert functionality
/// Equivalent to Angular's weight-only mode string resources
struct WeightOnlyModeStrings {
    // Weight-only mode indicator
    static let indicatorAccessibilityLabel = "Weight Only Mode Indicator"
    static let indicatorDescription = "Tap to see weight-only mode options"

    // Alert dialog
    static let alertTitle = "Weight Only Mode Active"
    static let alertMessage = "Another user has enabled Weight Only Mode on this scale. Only weight measurements will be collected."
    static let dismissAlert = "Dismiss"
    static let enableForSession = "Enable for Session"

    // Information messages
    static let noteTitle = "NOTE:"
    static let temporaryEnableNote = "You can temporarily enable body metrics for this session. "
        + "The scale will return to Weight Only Mode after this measurement."
    static let bodyMetricsEnabledMessage = "Body metrics enabled for this session"
    static let noScalesFoundMessage = "No scales found with Weight Only Mode enabled"

    // Scale status messages
    static let weightOnlyModeActive = "Weight Only Mode Active"
    static let enabledByOthers = "Enabled by other users"
    static let temporaryOverride = "All body metrics temporarily on"

    // Error messages
    static let enableFailedTitle = "Enable Failed"
    static let enableFailedMessage = "Unable to enable body metrics. Please try again."
    static let connectionRequiredMessage = "Scale must be connected to enable body metrics"

    // Success messages
    static let successTitle = "Success"
    static let errorTitle = "Error"
}
