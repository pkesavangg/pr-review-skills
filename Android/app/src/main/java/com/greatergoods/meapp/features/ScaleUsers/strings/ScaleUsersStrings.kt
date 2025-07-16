package com.greatergoods.meapp.features.ScaleUsers.strings

/**
 * Strings for Scale Users screens and components.
 */
object ScaleUsersStrings {
  const val UserProfileIcon = "User's weight only mode on"
  const val DeleteUser = "Delete user"
  const val OtherUsers = "Other Users"
  const val MaxUsers = "max: 10"
  const val Header = "Users"
  const val SaveButton = "Save"
  const val NoUsers = "No other users"
  const val LoaderMessage = "Saving..."
  fun LastActiveOn(date: String) = "last active on $date"

  /**
   * Toast messages for Scale Users.
   */
  object Toast {
    const val Success = "Users updated successfully"
    const val Error = "Failed to update users"
    const val LoadError = "Failed to load users"
  }

  object DeleteUserAlert {
    const val Title = "Are you sure you want to delete?"
    fun Message(username: String) =
      "Deleting $username will remove them as a user of the scale and they’ll need to reconnect."

    const val Delete = "Delete"
    const val Back = "Back"
  }
}
