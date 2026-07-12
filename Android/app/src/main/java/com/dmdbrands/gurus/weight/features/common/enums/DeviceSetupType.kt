package com.dmdbrands.gurus.weight.features.common.enums

import com.dmdbrands.gurus.weight.features.common.strings.DeviceSourceStrings
import com.dmdbrands.gurus.weight.features.common.strings.DeviceStrings
import kotlinx.serialization.Serializable

/**
 * Enum representing the setup type of a scale.
 */
@Serializable
enum class DeviceSetupType(
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
    val weightScaleTypes: Set<DeviceSetupType> = setOf(Wifi, Bluetooth, Lcbt, EspTouchWifi, BtWifiR4, AppSync)

    /** Setup types that represent a Blood Pressure Monitor (A3 and A6 protocol monitors). */
    val bloodPressureTypes: Set<DeviceSetupType> = setOf(BpmBluetooth, BpmA6Bluetooth)

    /** Setup types that represent a Baby Scale. */
    val babyScaleTypes: Set<DeviceSetupType> = setOf(BabyScale)

    fun fromString(value: String?): DeviceSetupType? = values().find { it.value == value }

    /** Returns true when [value] maps to a Weight Scale setup type. */
    fun isWeightScale(value: String?): Boolean = fromString(value) in weightScaleTypes

    /** Returns true when [value] maps to a Blood Pressure Monitor setup type. */
    fun isBloodPressure(value: String?): Boolean = fromString(value) in bloodPressureTypes

    /** Returns true when [value] maps to a Baby Scale setup type. */
    fun isBabyScale(value: String?): Boolean = fromString(value) in babyScaleTypes

    /**
     * Returns the display label for the given scale setup type.
     *
     * @param value The scale setup type value
     * @return The display label for the setup type
     */
    fun toLabel(value: String?): String = when (fromString(value)) {
      Wifi, EspTouchWifi -> DeviceStrings.Wifi
      BtWifiR4 -> DeviceStrings.BluetoothWifi
      Bluetooth, Lcbt, BpmBluetooth, BpmA6Bluetooth, BabyScale -> DeviceStrings.Bluetooth
      AppSync -> DeviceStrings.AppSync
      null -> DeviceStrings.Bluetooth // Default fallback
    }

    fun toSource(value: String): String = when (fromString(value)) {
      Wifi, EspTouchWifi -> DeviceSourceStrings.Wifi
      BtWifiR4 -> DeviceSourceStrings.BluetoothWifi
      Bluetooth, Lcbt, BpmBluetooth, BpmA6Bluetooth, BabyScale -> DeviceSourceStrings.Bluetooth
      AppSync -> DeviceSourceStrings.Appsync
      null -> DeviceSourceStrings.Bluetooth // Default fallback
    }
  }
}
