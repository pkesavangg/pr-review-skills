package com.dmdbrands.gurus.weight.features.common.model

import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
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
)

val SCALES =
  listOf(
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
    ScaleInfo("Smart Baby Scale", "0220", ScaleSetupType.BabyScale, false, createdAt = null),
    ScaleInfo("Smart Baby Scale", "0222", ScaleSetupType.BabyScale, false, createdAt = null),
    ScaleInfo("Smart Blood Pressure Monitor", "0663", ScaleSetupType.Bluetooth, false, createdAt = null),
  )
