package com.greatergoods.meapp.features.common.helper

import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.common.model.ScaleInfo

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
        val productName = this.sku?.let { sku ->
            SCALES.find { it.sku == sku }?.productName
        } ?: ""

        return ScaleInfo(
            productName = this.nickname?.takeIf { it.isNotBlank() } ?: productName,
            sku = this.sku ?: "",
            setupType = setupType,
            bodyComp = this.bodyComp,
            isConnected = this.isConnected,
            isWifiConfigured = this.isWifiConfigured,
            broadcastId = this.broadcastId,
        )
    }
}
