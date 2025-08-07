package com.dmdbrands.gurus.weight.features.landing.strings

import com.dmdbrands.gurus.weight.BuildConfig

object LandingString {
    const val Login = "Login"
    const val SignUp = "Sign Up"
    const val Version = "version ${BuildConfig.VERSION_NAME}"
    object MultiAccountLandingScreenStrings {
        const val LogIntoExistingAccount = "Log Into Existing Account"
        const val CreateNewAccount = "Create New Account"
    }
}
