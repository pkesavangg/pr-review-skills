package com.dmdbrands.gurus.weight.features.common.enums

/**
 * Enum representing the available scale segments for filtering scales.
 */
enum class DeviceSegmentType(
    val value: String,
) {
    All("All"),
    Bluetooth("Bluetooth"),
    Wifi("Wifi"),
    AppSync("AppSync"),
}
