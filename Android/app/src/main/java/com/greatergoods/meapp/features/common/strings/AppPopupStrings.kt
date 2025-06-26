package com.greatergoods.meapp.features.common.strings

/**
 * Static strings for AppPopup composable.
 */
object AppPopupStrings {
    const val LogoContentDescription = "Logo"

    object UnsavedChanges {
        const val Title = "Confirm"
        const val ManualEntryTitle = "Your entry has not been saved!"
        const val Message = "Are you sure you want to leave?"
        const val Return = "Return"
        const val Exit = "Exit"
    }

    object MaxAccountReachedAlert {
        const val Title = "Maximum Users Reached"

        const val Message =
            "You have reached the maximum number of accounts (10) on this device. Please remove an account before adding a new one."
        const val ConfirmButton = "OK"
    }

    object RemoveAccountDialog {
        const val Title = "Remove %s?"
        const val Message = "Are you sure you want to remove %s from this device?"
        const val CancelButton = "Cancel"
        const val ConfirmButton = "Remove"
        const val Loader = "Removing Account..."
    }
}
