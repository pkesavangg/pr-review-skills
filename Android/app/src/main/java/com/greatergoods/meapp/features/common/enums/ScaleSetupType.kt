package com.greatergoods.meapp.features.common.enums

import com.greatergoods.meapp.features.common.strings.ScaleStrings
import kotlinx.serialization.Serializable

/**
 * Enum representing the setup type of a scale.
 */
@Serializable
enum class ScaleSetupType(
  val value: String,
) {
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
  AppSync("appsync"),
  ;

  companion object {
    fun fromString(value: String?): ScaleSetupType? = values().find { it.value == value }

    /**
     * Returns the display label for the given scale setup type.
     *
     * @param value The scale setup type value
     * @return The display label for the setup type
     */
    fun toLabel(value: String?): String = when (fromString(value)) {
      Wifi, EspTouchWifi -> ScaleStrings.Wifi
      BtWifiR4 -> ScaleStrings.BluetoothWifi
      Bluetooth, Lcbt -> ScaleStrings.Bluetooth
      AppSync -> ScaleStrings.AppSync
      null -> ScaleStrings.Bluetooth // Default fallback
    }
  }
}
