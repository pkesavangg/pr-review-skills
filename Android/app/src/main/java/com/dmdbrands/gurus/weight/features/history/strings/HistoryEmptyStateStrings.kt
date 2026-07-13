package com.dmdbrands.gurus.weight.features.history.strings

/**
 * Copy for the History screen empty states, keyed by product and device/entry state.
 *
 * The state shown depends on whether a device is paired and whether any entries exist:
 * - No device paired  → "connect a device" prompt with an ADD DEVICE CTA.
 * - Device paired, no entries yet → "log manually" prompt with a LOG MANUALLY CTA.
 *
 * Wording is product-specific: Weight uses "scale", Blood Pressure uses "monitor",
 * Baby uses "baby scale". (MOB-1221)
 */
object HistoryEmptyStateStrings {

    // Shared CTA labels
    const val AddDevice = "Add Device"
    const val LogManually = "Log Manually"

    // Weight Scale
    const val WeightNoDeviceTitle = "No scale connected"
    const val WeightNoDeviceDescription = "Add a weight scale to start monitoring your weight."
    const val WeightNoEntryTitle = "No measurements yet"
    const val WeightNoEntryDescription = "Take a reading using your scale or log a measurement manually."

    // Blood Pressure Monitor
    const val BpmNoDeviceTitle = "No monitor connected"
    const val BpmNoDeviceDescription = "Add a blood pressure monitor to start tracking your readings."
    const val BpmNoEntryTitle = "No readings yet"
    const val BpmNoEntryDescription = "Take a reading using your monitor or log one manually."

    // Baby Scale (a baby profile already exists in these two states)
    const val BabyNoDeviceTitle = "No baby scale paired yet"
    const val BabyNoDeviceDescription = "Connect a baby scale to start tracking, or log a measurement manually."
    const val BabyNoEntryTitle = "No measurements yet"
    const val BabyNoEntryDescription = "Weigh your baby using your scale or log a measurement manually."

    // Icon content descriptions
    const val WeightIconDescription = "Weight scale"
    const val BpmIconDescription = "Blood pressure monitor"
    const val BabyScaleIconDescription = "Baby scale"
    const val NoEntriesIconDescription = "No entries"
}
