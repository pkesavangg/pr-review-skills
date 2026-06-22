package com.dmdbrands.gurus.weight.features.signup.strings

/**
 * Strings for the signup error screen shown when a device's profile / product
 * creation fails (network / data-retrieval / backend) — see MOB-420.
 *
 * Account-creation failures are NOT shown here: they surface as a toast on the
 * password step ([accountFailedToast]). The error screen is reserved for
 * per-device product-creation failures.
 */
object SignupErrorStrings {
    const val title = "Something went wrong."
    const val subtitle = "We couldn't save your profile. Check your connection and try again."

    // Per-device status copy in the device list.
    const val deviceSuccess = "Added to your profile"
    const val deviceFailure = "Profile couldn't be saved — tap Try Again"
    const val devicePending = "Not yet started"

    // CANCEL → FINISH on Android: the account and already-saved devices persist,
    // so the left action completes signup rather than discarding it (MOB-420).
    const val finish = "FINISH"
    const val tryAgain = "TRY AGAIN"

    // Shown when account creation itself fails on the password step.
    const val accountFailedToast = "We couldn't create your account. Please try again."
}
