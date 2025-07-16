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

  object AccountSwitchInfo {
    const val Header = "NEW: Add Multiple Accounts"
    const val Message =
      "Switch between Weight Gurus accounts by pressing and holding the profile icon or selecting \"Switch Accounts\" in settings."
    const val AddAccountButton = "ADD ACCOUNT"
    const val CloseContentDescription = "Close"
  }

  object ModelNumberHelpPopup {
    const val Title = "Where to find your model number"
    const val Message =
      "Check the back of your scale for a sticker with your four-digit model number.\n\nFor example, if you have a 0375 Bluetooth Scale, the sticker will show the URL: greatergoods.com/0375"
    const val CloseContentDescription = "Close"
    const val ImageContentDescription = "Example sticker showing model number location on scale"
  }

  object UnsavedExitPopup {
    const val Title = "Unsaved Changes"
    const val Message = "Are you sure you want to leave this page? Changes you made will not be saved. "
    const val Leave = "Leave"
    const val Cancel = "Cancel"
  }

  object ScaleDiscoveredPopup {
    const val Title = "New Scale Discovered"
  }
}
