package com.dmdbrands.gurus.weight.features.landing.strings

import com.dmdbrands.gurus.weight.BuildConfig

object LandingString {
  const val Login = "Log In"
  const val SignUp = "Sign Up"
  const val Version = "Version ${BuildConfig.VERSION_NAME}"

  object MultiAccountLandingScreenStrings {
    const val LogIntoExistingAccount = "Log Into Existing Account"
    const val CreateNewAccount = "Create New Account"
  }

  // region Accessibility (TalkBack)
  /** Brand logo description spoken by TalkBack on the landing banner. */
  const val accLogoLabel = "me.health by greater goods"
  // endregion
}
