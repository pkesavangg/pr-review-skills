package com.dmdbrands.gurus.weight.features.DeviceUsers.strings

/**
 * Strings for Scale Users screens and components.
 */
object DeviceUsersStrings {
  const val UserProfileIcon = "User's weight only mode on"
  const val DeleteUser = "Delete user"
  const val OtherUsers = "Other Users"
  const val MaxUsers = "max: 10"
  const val Header = "Users"
  const val SaveButton = "Save"
  const val NoUsers = "No other users"
  const val LoaderMessage = "Saving..."
  const val Loading = "Loading..."
  fun LastActiveOn(date: String) = "last active on $date"

  /**
   * Toast messages for Scale Users.
   */
  object Toast {
    const val Success = "Users updated successfully"
    const val Error = "Failed to update users"
    const val LoadError = "Failed to load users"
    const val UserDeleted = "User deleted successfully"
    const val DeviceDeleted = "Scale deleted successfully"
  }

  object DeleteUserAlert {
    const val Title = "Are you sure you want to delete?"
    fun Message(username: String) =
      "Deleting $username will remove them as a user of the scale and they'll need to reconnect."

    const val Delete = "Delete"
    const val Back = "Back"
  }

  object DeleteScaleAlert {
    const val Title = "Delete Scale"
    const val Message = "Are you sure you want to delete this scale? This will remove the scale from your account and you'll need to set it up again."
    const val Delete = "Delete Scale"
    const val Cancel = "Cancel"
  }

  // region Accessibility (TalkBack)
  /** Spoken label for the icon-only close button in the app bar. */
  const val accCloseLabel = "Close"
  // endregion
}
