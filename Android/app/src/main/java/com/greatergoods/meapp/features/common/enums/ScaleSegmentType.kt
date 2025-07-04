package com.greatergoods.meapp.features.common.enums

/**
 * Enum representing the available scale segments for filtering scales.
 */
enum class ScaleSegmentType(
    val value: String,
) {
    All("All"),
    Bluetooth("Bluetooth"),
    Wifi("Wifi"),
    AppSync("AppSync"),
}
