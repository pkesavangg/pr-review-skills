package com.greatergoods.meapp.features.common.model

import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import kotlinx.serialization.Serializable

/**
 * Data class representing a scale's information.
 * @property productName The name of the scale product.
 * @property sku The SKU/model number.
 * @property setupType The setup type (WiFi, Bluetooth, etc.).
 * @property bodyComp Whether the scale supports body composition.
 * @property isConnected Whether the scale is currently connected.
 * @property isWifiConfigured Whether the scale is WiFi configured.
 * @property broadcastId The broadcast ID of the scale.
 */
@Serializable
data class ScaleInfo(
    val productName: String,
    val sku: String,
    val setupType: ScaleSetupType,
    val bodyComp: Boolean,
    val isConnected: Boolean? = null,
    val isWifiConfigured: Boolean? = null,
    val broadcastId: String? = null,
)

val SCALES =
    listOf(
        ScaleInfo("AppSync Body Fat Scale", "0341", ScaleSetupType.AppSync, true),
        ScaleInfo("AppSync Bathroom Scale", "0342", ScaleSetupType.AppSync, false),
        ScaleInfo("AppSync Body Fat Scale", "0343", ScaleSetupType.AppSync, true),
        ScaleInfo("AppSync Body Fat Scale", "0345", ScaleSetupType.AppSync, true),
        ScaleInfo("AppSync Body Fat Scale", "0346", ScaleSetupType.AppSync, true),
        ScaleInfo("AppSync Body Fat Scale", "0347", ScaleSetupType.AppSync, true),
        ScaleInfo("Basic AppSync Bathroom Scale", "0358", ScaleSetupType.AppSync, false),
        ScaleInfo("Basic AppSync Bathroom Scale", "0359", ScaleSetupType.AppSync, false),
        ScaleInfo("AppSync Bathroom Scale", "0364", ScaleSetupType.Wifi, true),
        ScaleInfo("AppSync Body Fat Scale", "0369", ScaleSetupType.Wifi, true),
        ScaleInfo("AppSync Body Fat Scale", "0370", ScaleSetupType.Wifi, true),
        ScaleInfo("AppSync Bathroom Scale", "0371", ScaleSetupType.Wifi, false),
        ScaleInfo("Bluetooth Smart Scale", "0375", ScaleSetupType.Bluetooth, false),
        ScaleInfo("Bluetooth Smart Scale", "0376", ScaleSetupType.Bluetooth, false),
        ScaleInfo("Bluetooth Smart Scale", "0378", ScaleSetupType.Lcbt, true),
        ScaleInfo("Bluetooth Smart Scale", "0380", ScaleSetupType.Bluetooth, false),
        ScaleInfo("Bluetooth Smart Scale", "0382", ScaleSetupType.Bluetooth, true),
        ScaleInfo("Bluetooth Scale", "0383", ScaleSetupType.Lcbt, true),
        ScaleInfo("Wi-Fi Smart Scale", "0384", ScaleSetupType.EspTouchWifi, true),
        ScaleInfo("Wi-Fi Smart Scale", "0385", ScaleSetupType.Wifi, true),
        ScaleInfo("Wi-Fi Smart Scale", "0396", ScaleSetupType.Wifi, false),
        ScaleInfo("Wi-Fi Smart Scale", "0397", ScaleSetupType.EspTouchWifi, false),
        ScaleInfo("AccuCheck Verve Smart Scale", "0412", ScaleSetupType.BtWifiR4, true),
    )
