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

        const val Message1 = "Please swipe left to remove any unused accounts before attempting to add a new one."
        const val Message2 = "Log in to a saved account, then open Settings and tap Switch Accounts to remove users."
        const val ConfirmButton = "OK"
    }

    object RemoveAccountDialog {
        const val Title = "Remove %s?"
        const val Message = "Are you sure you want to remove %s from this device?"
        const val CancelButton = "Cancel"
        const val ConfirmButton = "Remove"
        const val Loader = "Removing Account..."
    }

    object BackgroundLoggedOutAlert {
        fun Title(username: String) = "$username was logged out"
        const val Message = "Please log back in to continue."
        const val ConfirmButton = "OK"
    }
}
