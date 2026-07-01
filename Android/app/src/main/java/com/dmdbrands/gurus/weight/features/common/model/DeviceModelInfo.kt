package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import kotlinx.serialization.Serializable

/**
 * Data class representing a scale's information.
 * @property productName The name of the scale product.
 * @property sku The SKU/model number.
 * @property setupType The setup type (WiFi, Bluetooth, etc.).
 * @property bodyComp Whether the scale supports body composition.
 * @property isConnected Whether the scale is currently connected.
 * @property isWifiConfigured Whether the scale is WiFi configured.
 * @property scaleId The unique identifier of the scale.
 * @property createdAt The timestamp when the scale was created.
 * @property hasNumericUsers Whether the device uses numeric user labels (1/2) or alphabetic (A/B). Only relevant for BPM devices; ignored for other scales.
 * @property userNumber The assigned user slot (1 or 2) for BPM devices; null for non-BPM scales or when unassigned.
 */
@Serializable
data class DeviceModelInfo(
  val productName: String,
  val sku: String,
  val setupType: DeviceSetupType,
  val bodyComp: Boolean,
  val isConnected: Boolean? = null,
  val isWifiConfigured: Boolean? = null,
  val scaleId: String? = null,
  val createdAt: String? = null,
  val hasNumericUsers: Boolean = true,
  val userNumber: Int? = null,
)

val DEVICES =
  listOf(
    DeviceModelInfo("AppSync Body Fat Scale", "0340", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0341", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Bathroom Scale", "0342", DeviceSetupType.AppSync, false, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0343", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0345", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0346", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0347", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("Basic AppSync Bathroom Scale", "0358", DeviceSetupType.AppSync, false, createdAt = null),
    DeviceModelInfo("Basic AppSync Bathroom Scale", "0359", DeviceSetupType.AppSync, false, createdAt = null),
    DeviceModelInfo("AppSync Bathroom Scale", "0364", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0369", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Body Fat Scale", "0370", DeviceSetupType.AppSync, true, createdAt = null),
    DeviceModelInfo("AppSync Bathroom Scale", "0371", DeviceSetupType.AppSync, false, createdAt = null),
    DeviceModelInfo("Bluetooth Smart Scale", "0375", DeviceSetupType.Bluetooth, false, createdAt = null),
    DeviceModelInfo("Bluetooth Smart Scale", "0376", DeviceSetupType.Bluetooth, false, createdAt = null),
    DeviceModelInfo("Bluetooth Smart Scale", "0378", DeviceSetupType.Lcbt, true, createdAt = null),
    DeviceModelInfo("Bluetooth Smart Scale", "0380", DeviceSetupType.Bluetooth, false, createdAt = null),
    DeviceModelInfo("Bluetooth Smart Scale", "0382", DeviceSetupType.Bluetooth, true, createdAt = null),
    DeviceModelInfo("Bluetooth Scale", "0383", DeviceSetupType.Lcbt, true, createdAt = null),
    DeviceModelInfo("Wi-Fi Smart Scale", "0384", DeviceSetupType.EspTouchWifi, true, createdAt = null),
    DeviceModelInfo("Wi-Fi Smart Scale", "0385", DeviceSetupType.Wifi, true, createdAt = null),
    DeviceModelInfo("Wi-Fi Smart Scale", "0396", DeviceSetupType.Wifi, false, createdAt = null),
    DeviceModelInfo("Wi-Fi Smart Scale", "0397", DeviceSetupType.EspTouchWifi, false, createdAt = null),
    DeviceModelInfo("AccuCheck Verve Smart Scale", "0412", DeviceSetupType.BtWifiR4, true, createdAt = null),
    // Baby Scales
    DeviceModelInfo("Smart Baby Scale", "0220", DeviceSetupType.BabyScale, false, createdAt = null),
    DeviceModelInfo("Smart Baby Scale", "0222", DeviceSetupType.BabyScale, false, createdAt = null),
    // Blood Pressure Monitors
    DeviceModelInfo("Smart Wrist Blood Pressure Monitor", "0603", DeviceSetupType.BpmBluetooth, false, createdAt = null),
    DeviceModelInfo("Smart Blood Pressure Monitor", "0604", DeviceSetupType.BpmBluetooth, false, createdAt = null),
    DeviceModelInfo("Smart Pro-Series Blood Pressure Monitor", "0634", DeviceSetupType.BpmBluetooth, false, createdAt = null),
    DeviceModelInfo("All-In-One Bluetooth Blood Pressure Monitor", "0636", DeviceSetupType.BpmBluetooth, false, createdAt = null),
    DeviceModelInfo("Smart Blood Pressure Monitor", "0661", DeviceSetupType.BpmA6Bluetooth, false, createdAt = null),
    DeviceModelInfo("Smart Blood Pressure Monitor", "0663", DeviceSetupType.BpmA6Bluetooth, false, createdAt = null),
  )

/** Baby scales — derived from [SCALES] via [DeviceHelper.BABY_SCALE_SKUS]. */
val BABY_SCALES: List<DeviceModelInfo> = DEVICES.filter { it.sku in DeviceHelper.BABY_SCALE_SKUS }

/** Blood Pressure Monitors — derived from [SCALES] via [DeviceHelper.BPM_SKUS]. */
val BPM_DEVICES: List<DeviceModelInfo> = DEVICES.filter { it.sku in DeviceHelper.BPM_SKUS }

/** Weight scales only — [SCALES] excluding baby scales and BPM devices. */
val WEIGHT_SCALES: List<DeviceModelInfo> = DEVICES.filterNot {
  it.sku in DeviceHelper.BABY_SCALE_SKUS || it.sku in DeviceHelper.BPM_SKUS
}
