package com.dmdbrands.gurus.weight.features.appSync.strings

/**
 * String constants for the AppSync (scanner) feature.
 */
object AppSyncStrings {
    // region Accessibility (TalkBack)
    /**
     * Spoken prefix announced (via a polite live region) when the AppSync scan-result
     * popup appears, so TalkBack users hear that the scan succeeded before the metrics
     * are read out. The metric values themselves are composed at the call site from the
     * already-localised popup strings.
     */
    const val accScanResultLabel = "AppSync scan result"
    // endregion
}
