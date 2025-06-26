package com.greatergoods.meapp.features.export.strings

/**
 * Strings for the Export functionality.
 */
object ExportStrings {
    // Dialog/Modal strings
    const val ExportDialogTitle = "Download Weight History"
    const val ExportDialogMessage = "An email with your measurement history will be sent to the email address associated with this account."
    const val SendButton = "Send"
    const val CancelButton = "Cancel"

    // Loading/Success/Error messages
    const val LoaderMessage = "Sending .CSV file..."
    const val SuccessMessage = ".CSV file sent. Please check your email."
    const val ErrorMessage = "Failed to export data. Please try again."

    // Log messages
    const val ExportStarted = "Export data process started"
    const val ExportCompleted = "Export data completed successfully"
    const val ExportFailed = "Export data failed"
}
