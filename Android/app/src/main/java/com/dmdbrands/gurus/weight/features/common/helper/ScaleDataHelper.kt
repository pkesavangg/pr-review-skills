package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.resources.AppIcons

/**
 * Helper object providing conversion functions for scale data.
 */
object ScaleDataHelper {
  /**
   * Finds ScaleInfo by SKU, mapping variant SKUs (e.g., 0022 -> 0383) for lookup.
   * @param sku The SKU to look up (can be original or variant SKU)
   * @return The ScaleInfo if found, null otherwise
   */
  fun findScaleInfoBySku(sku: String): ScaleInfo? {
    val lookupSku = DeviceHelper.mapSkuForDisplay(sku)
    return SCALES.find { it.sku == lookupSku }
  }

  fun isBpmDevice(sku: String?): Boolean {
    val mapped = sku?.let { DeviceHelper.mapSkuForDisplay(it) }
    return mapped in DeviceHelper.BPM_SKUS
  }

  /**
   * Converts a GGDevice to ScaleInfo for UI display.
   */
  fun Device.toScaleInfo(): ScaleInfo {
    val setupType =
      when (this.deviceType?.lowercase()) {
        "wifi", "esptouchwifi" -> ScaleSetupType.Wifi
        "bluetooth", "lcbt" -> ScaleSetupType.Bluetooth
        "babyscale" -> ScaleSetupType.BabyScale
        "btwifir4" -> ScaleSetupType.BtWifiR4
        "appsync" -> ScaleSetupType.AppSync
        else -> ScaleSetupType.Bluetooth // Default fallback
      }

    // Get stored SKU and find scale info (maps 0022 -> 0383 internally)
    val storedSku = this.getSKU()
    val scaleInfoFromScales = findScaleInfoBySku(storedSku)
    val displaySku = scaleInfoFromScales?.sku ?: DeviceHelper.mapSkuForDisplay(storedSku)
    val productName = scaleInfoFromScales?.productName ?: this.nickname
    val bodyComp = scaleInfoFromScales?.bodyComp ?: false

    return ScaleInfo(
      productName = if (this.nickname.isNotBlank()) this.nickname else productName,
      sku = displaySku,
      setupType = setupType,
      bodyComp = bodyComp,
      isConnected = this.connectionStatus == BLEStatus.CONNECTED,
      isWifiConfigured = this.device?.isWifiConfigured == true,
      createdAt = this.createdAt,
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
      ScaleSetupType.Bluetooth, ScaleSetupType.Lcbt, ScaleSetupType.BabyScale -> AppIcons.Connection.Bluetooth
      ScaleSetupType.BtWifiR4 -> AppIcons.Connection.BluetoothWifi
      ScaleSetupType.AppSync -> AppIcons.Connection.AppSync
    }
}
