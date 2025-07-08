package com.greatergoods.meapp.features.common.helper

import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.model.ScaleInfo
import com.greatergoods.meapp.resources.AppIcons

/**
 * Helper object providing conversion functions for scale data.
 */
object ScaleDataHelper {
    /**
     * Converts a Device to ScaleInfo for UI display.
     */
    fun Device.toScaleInfo(): ScaleInfo {
        val setupType =
            when (this.deviceType?.lowercase()) {
                "wifi", "esptouchwifi" -> ScaleSetupType.Wifi
                "bluetooth", "lcbt" -> ScaleSetupType.Bluetooth
                "btwifir4" -> ScaleSetupType.BtWifiR4
                "appsync" -> ScaleSetupType.AppSync
                else -> ScaleSetupType.Bluetooth // Default fallback
            }

        // Get product name from SCALES using sku
        val productName =
            this.sku?.let { sku ->
                SCALES.find { it.sku == sku }?.productName
            } ?: ""

        return ScaleInfo(
            productName = this.nickname?.takeIf { it.isNotBlank() } ?: productName,
            sku = this.sku ?: "",
            setupType = setupType,
            bodyComp = this.bodyComp,
            isConnected = this.isConnected,
            isWifiConfigured = this.isWifiConfigured,
            scaleId = this.id,
        )
    }

    /**
     * Returns the appropriate icon for the given scale setup type.
     *
     * @param setupType The scale setup type
     * @return The icon resource ID for the setup type
     */
    fun scaleTypeIcon(setupType: ScaleSetupType): Int =
        when (setupType) {
            ScaleSetupType.Wifi, ScaleSetupType.EspTouchWifi -> AppIcons.Connection.Wifi
            ScaleSetupType.Bluetooth, ScaleSetupType.Lcbt -> AppIcons.Connection.Bluetooth
            ScaleSetupType.BtWifiR4 -> AppIcons.Connection.BluetoothWifi
            ScaleSetupType.AppSync -> AppIcons.Connection.AppSync
        }
}
