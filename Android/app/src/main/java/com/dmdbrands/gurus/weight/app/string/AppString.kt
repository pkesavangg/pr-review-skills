package com.dmdbrands.gurus.weight.app.string

object AppString {
  const val SCALEDISCOVEREDTIMEOUT = 15_000L
  object Alert {
    object MaxUser {
      const val title = "Scale is at Its User Limit"
      const val message =
        "Your connection was deactivated by another user. Reconnect now or delete the scale from your account by visiting scale settings. "
    }

    object DuplicateUser {
      const val title = "Duplicate Scale User Name"
      const val message = "Reconnect the scale with a new user name."
    }
  }

  const val Reconnect = "Reconnect"
  const val Cancel = "Cancel"
}
