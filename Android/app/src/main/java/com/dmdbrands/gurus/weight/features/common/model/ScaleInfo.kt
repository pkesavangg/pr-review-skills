package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
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
data class ScaleInfo(
  val productName: String,
  val sku: String,
  val setupType: ScaleSetupType,
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
    ScaleInfo("AppSync Body Fat Scale", "0340", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0341", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Bathroom Scale", "0342", ScaleSetupType.AppSync, false, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0343", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0345", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0346", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0347", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("Basic AppSync Bathroom Scale", "0358", ScaleSetupType.AppSync, false, createdAt = null),
    ScaleInfo("Basic AppSync Bathroom Scale", "0359", ScaleSetupType.AppSync, false, createdAt = null),
    ScaleInfo("AppSync Bathroom Scale", "0364", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0369", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Body Fat Scale", "0370", ScaleSetupType.AppSync, true, createdAt = null),
    ScaleInfo("AppSync Bathroom Scale", "0371", ScaleSetupType.AppSync, false, createdAt = null),
    ScaleInfo("Bluetooth Smart Scale", "0375", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Bluetooth Smart Scale", "0376", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Bluetooth Smart Scale", "0378", ScaleSetupType.Lcbt, true, createdAt = null),
    ScaleInfo("Bluetooth Smart Scale", "0380", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Bluetooth Smart Scale", "0382", ScaleSetupType.Bluetooth, true, createdAt = null),
    ScaleInfo("Bluetooth Scale", "0383", ScaleSetupType.Lcbt, true, createdAt = null),
    ScaleInfo("Wi-Fi Smart Scale", "0384", ScaleSetupType.EspTouchWifi, true, createdAt = null),
    ScaleInfo("Wi-Fi Smart Scale", "0385", ScaleSetupType.Wifi, true, createdAt = null),
    ScaleInfo("Wi-Fi Smart Scale", "0396", ScaleSetupType.Wifi, false, createdAt = null),
    ScaleInfo("Wi-Fi Smart Scale", "0397", ScaleSetupType.EspTouchWifi, false, createdAt = null),
    ScaleInfo("AccuCheck Verve Smart Scale", "0412", ScaleSetupType.BtWifiR4, true, createdAt = null),
    // Baby Scales
    ScaleInfo("Smart Baby Scale", "0220", ScaleSetupType.BabyScale, false, createdAt = null),
    ScaleInfo("Smart Baby Scale", "0222", ScaleSetupType.BabyScale, false, createdAt = null),
    // Blood Pressure Monitors
    ScaleInfo("Smart Wrist Blood Pressure Monitor", "0603", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Smart Blood Pressure Monitor", "0604", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Smart Pro-Series Blood Pressure Monitor", "0634", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("All-In-One Bluetooth Blood Pressure Monitor", "0636", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Smart Blood Pressure Monitor", "0661", ScaleSetupType.Bluetooth, false, createdAt = null),
    ScaleInfo("Smart Blood Pressure Monitor", "0663", ScaleSetupType.Bluetooth, false, createdAt = null),
  )

/** Baby scales — derived from [SCALES] via [DeviceHelper.BABY_SCALE_SKUS]. */
val BABY_SCALES: List<ScaleInfo> = DEVICES.filter { it.sku in DeviceHelper.BABY_SCALE_SKUS }

/** Blood Pressure Monitors — derived from [SCALES] via [DeviceHelper.BPM_SKUS]. */
val BPM_DEVICES: List<ScaleInfo> = DEVICES.filter { it.sku in DeviceHelper.BPM_SKUS }

/** Weight scales only — [SCALES] excluding baby scales and BPM devices. */
val WEIGHT_SCALES: List<ScaleInfo> = DEVICES.filterNot {
  it.sku in DeviceHelper.BABY_SCALE_SKUS || it.sku in DeviceHelper.BPM_SKUS
}
