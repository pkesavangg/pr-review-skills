package com.greatergoods.meapp.features.common.enums

import kotlinx.serialization.Serializable

/**
 * Enum representing the setup type of a scale.
 */
@Serializable
enum class ScaleSetupType(val value: String) {
    /** WiFi setup type. */
    Wifi("wifi"),

    /** Bluetooth setup type. */
    Bluetooth("bluetooth"),

    /** Low-cost Bluetooth setup type. */
    Lcbt("lcbt"),

    /** EspTouch WiFi setup type. */
    EspTouchWifi("espTouchWifi"),

    /** Bluetooth + WiFi R4 setup type. */
    BtWifiR4("btWifiR4"),

    /** AppSync setup type. */
    AppSync("appSync");

    companion object {
        fun fromString(value: String?): ScaleSetupType? = values().find { it.value == value }
    }
}
