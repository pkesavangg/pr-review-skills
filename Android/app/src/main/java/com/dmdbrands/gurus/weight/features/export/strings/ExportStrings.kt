package com.dmdbrands.gurus.weight.features.export.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Strings for the Export functionality.
 */
object ExportStrings {
    // Dialog/Modal strings
    // Export delivers the history via email, not a device download, so the title/body/CTA all
    // use "Send" wording (MOB-1230 / UX query MOB-652). Applied to all products for consistency.
    const val ExportDialogTitle = "Send Weight History"
    const val ExportBpDialogTitle = "Send BP History"
    const val ExportBabyDialogTitle = "Send Baby History"
    const val ExportDialogMessage = "We'll send your measurement history to the email address linked to your account."
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
