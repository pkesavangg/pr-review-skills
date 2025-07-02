package com.greatergoods.meapp.features.common.model

import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.resources.AppIcons

/**
 * Data class representing a scale's information.
 * @property productName The name of the scale product.
 * @property sku The SKU/model number.
 * @property imgPath Optional image path for the scale.
 * @property setupType The setup type (WiFi, Bluetooth, etc.).
 * @property bodyComp Whether the scale supports body composition.
 * @property isConnected Whether the scale is currently connected.
 * @property isWifiConfigured Whether the scale is WiFi configured.
 */
data class ScaleInfo(
    val productName: String,
    val sku: String,
    val imgPath: Int?,
    val setupType: ScaleSetupType,
    val bodyComp: Boolean,
    val isConnected: Boolean? = null,
    val isWifiConfigured: Boolean? = null,
)

val SCALES = listOf(

    ScaleInfo("Bluetooth Smart Scale", "0375", null, ScaleSetupType.Bluetooth, false),
    ScaleInfo("Bluetooth Smart Scale", "0376", null, ScaleSetupType.Bluetooth, false),
    ScaleInfo("Bluetooth Smart Scale", "0378", null, ScaleSetupType.Lcbt, true),
    ScaleInfo("Bluetooth Smart Scale", "0380", null, ScaleSetupType.Bluetooth, false),
    ScaleInfo("Bluetooth Smart Scale", "0382", null, ScaleSetupType.Bluetooth, true),
    ScaleInfo("Bluetooth Scale", "0383", null, ScaleSetupType.Lcbt, true),
    ScaleInfo("Wi-Fi Smart Scale", "0384", null, ScaleSetupType.EspTouchWifi, true),
    ScaleInfo("Wi-Fi Smart Scale", "0385", null, ScaleSetupType.Wifi, true),
    ScaleInfo("Wi-Fi Smart Scale", "0396", null, ScaleSetupType.Wifi, false),
    ScaleInfo("Wi-Fi Smart Scale", "0397", null, ScaleSetupType.EspTouchWifi, false),
    ScaleInfo("AccuCheck Verve Smart Scale", "0412", null, ScaleSetupType.BtWifiR4, true),
)

// TODO(): Add AppSync scales
// ScaleInfo("AppSync Body Fat Scale", "0341", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Bathroom Scale", "0342", null, ScaleSetupType.Wifi, false),
// ScaleInfo("AppSync Body Fat Scale", "0343", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Body Fat Scale", "0345", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Body Fat Scale", "0346", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Body Fat Scale", "0347", null, ScaleSetupType.Wifi, true),
// ScaleInfo("Basic AppSync Bathroom Scale", "0358", null, ScaleSetupType.Wifi, false),
// ScaleInfo("Basic AppSync Bathroom Scale", "0359", null, ScaleSetupType.Wifi, false),
// ScaleInfo("AppSync Bathroom Scale", "0364", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Body Fat Scale", "0369", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Body Fat Scale", "0370", null, ScaleSetupType.Wifi, true),
// ScaleInfo("AppSync Bathroom Scale", "0371", null, ScaleSetupType.Wifi, false),
