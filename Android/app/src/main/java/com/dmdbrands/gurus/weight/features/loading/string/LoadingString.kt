package com.dmdbrands.gurus.weight.features.loading.string

import com.dmdbrands.gurus.weight.BuildConfig

/**
 * String constants used in the Loading screen.
 */
object LoadingString {
    /** Content description for the logo image. */

    /** Footer branding text. */
    const val FOOTER_BRAND = "me.health by greater goods"

    /** Footer version text. */
    const val VERSION = "version ${BuildConfig.VERSION_NAME}"

    /** Base loading text. */
    const val LOADING = "loading"

    // region Accessibility (TalkBack)
    /** Brand logo description spoken by TalkBack on the splash banner. */
    const val accLogoLabel = "me.health by greater goods"
    // endregion
}
