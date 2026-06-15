package com.dmdbrands.gurus.weight.features.common.enums

import com.dmdbrands.gurus.weight.features.common.strings.ScaleSourceStrings
import com.dmdbrands.gurus.weight.features.common.strings.ScaleStrings
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

  /** BPM Bluetooth setup type (A3 protocol monitors: 0603, 0604, 0634, 0636). */
  BpmBluetooth("bpmBluetooth"),

  /** BPM A6 Bluetooth setup type (A6 protocol monitors: 0661, 0663). */
  BpmA6Bluetooth("bpmA6Bluetooth"),

  /** Baby Scale Bluetooth setup type. */
  BabyScale("babyScale"),
  ;

  companion object {
    /**
     * Setup types that represent a Weight Scale (as opposed to a Blood Pressure
     * Monitor or Baby Scale). Used to gate weight-scale-specific UI.
     */
    val weightScaleTypes: Set<ScaleSetupType> = setOf(Wifi, Bluetooth, Lcbt, EspTouchWifi, BtWifiR4, AppSync)

    fun fromString(value: String?): ScaleSetupType? = values().find { it.value == value }

    /** Returns true when [value] maps to a Weight Scale setup type. */
    fun isWeightScale(value: String?): Boolean = fromString(value) in weightScaleTypes

    /**
     * Returns the display label for the given scale setup type.
     *
     * @param value The scale setup type value
     * @return The display label for the setup type
     */
    fun toLabel(value: String?): String = when (fromString(value)) {
      Wifi, EspTouchWifi -> ScaleStrings.Wifi
      BtWifiR4 -> ScaleStrings.BluetoothWifi
      Bluetooth, Lcbt, BpmBluetooth, BpmA6Bluetooth, BabyScale -> ScaleStrings.Bluetooth
      AppSync -> ScaleStrings.AppSync
      null -> ScaleStrings.Bluetooth // Default fallback
    }

    fun toSource(value: String): String = when (fromString(value)) {
      Wifi, EspTouchWifi -> ScaleSourceStrings.Wifi
      BtWifiR4 -> ScaleSourceStrings.BluetoothWifi
      Bluetooth, Lcbt, BpmBluetooth, BpmA6Bluetooth, BabyScale -> ScaleSourceStrings.Bluetooth
      AppSync -> ScaleSourceStrings.Appsync
      null -> ScaleSourceStrings.Bluetooth // Default fallback
    }
  }
}
