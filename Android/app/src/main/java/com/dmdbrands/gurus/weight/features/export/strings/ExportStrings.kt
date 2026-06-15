package com.dmdbrands.gurus.weight.features.export.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Strings for the Export functionality.
 */
object ExportStrings {
    // Dialog/Modal strings
    const val ExportDialogTitle = "Download Weight History"
    const val ExportBpDialogTitle = "Download BP History"
    const val ExportBabyDialogTitle = "Download Baby History"
    const val ExportDialogMessage = "An email with your measurement history will be sent to the email address associated with this account."
    const val SendButton = "Send"
    const val CancelButton = "Cancel"

    /**
     * Returns the export confirmation dialog title for the active history type
     * so the copy matches the screen the export was started from.
     */
    fun exportDialogTitle(productType: ProductType): String = when (productType) {
        ProductType.MY_WEIGHT -> ExportDialogTitle
        ProductType.BLOOD_PRESSURE -> ExportBpDialogTitle
        ProductType.BABY -> ExportBabyDialogTitle
    }

    // Loading/Success/Error messages
    const val LoaderMessage = "Sending .CSV file..."
    const val SuccessMessage = ".CSV file sent. Please check your email."
    const val ErrorMessage = "Failed to export data. Please try again."

    // Log messages
    const val ExportStarted = "Export data process started"
    const val ExportCompleted = "Export data completed successfully"
    const val ExportFailed = "Export data failed"
}
