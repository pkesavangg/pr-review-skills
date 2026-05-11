package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.DEVICES
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.resources.AppIcons

/**
 * Helper object providing conversion functions for scale data.
 */
object ScaleDataHelper {
  private const val USER_A = "A"
  private const val USER_B = "B"

  /**
   * Finds [ScaleInfo] by SKU, mapping variant SKUs (e.g. 0022 -> 0383) for lookup.
   * @param sku original or variant SKU; `null` short-circuits to `null`.
   * @return matching [ScaleInfo], or `null` when [sku] is null or unknown.
   */
  fun findScaleInfoBySku(sku: String): ScaleInfo? {
    val lookupSku = DeviceHelper.mapSkuForDisplay(sku)
    val found = DEVICES.find { it.sku == lookupSku } ?: return null
    return if (sku != lookupSku) found.copy(sku = sku) else found
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
        "bpmbluetooth" -> ScaleSetupType.BpmBluetooth
        "bpma6bluetooth" -> ScaleSetupType.BpmA6Bluetooth
        else -> ScaleSetupType.Bluetooth // Default fallback
      }

    // Get stored SKU and find scale info (maps 0022 -> 0383 internally)
    val storedSku = this.getSKU()
    val scaleInfoFromScales = findScaleInfoBySku(storedSku)
    val displaySku = if (scaleInfoFromScales != null) storedSku else DeviceHelper.mapSkuForDisplay(storedSku)
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
      hasNumericUsers = scaleInfoFromScales?.hasNumericUsers ?: true,
      userNumber = this.userNumber,
    )
  }

  /**
   * Formats a BPM user-slot for display: numeric (`"1"`/`"2"`) when [hasNumericUsers] is `true`,
   * otherwise alphabetic (`"A"`/`"B"`). Returns an empty string for null or out-of-range
   * [userNumber] (callers should treat empty as "do not show").
   */
  fun formatUserDisplay(hasNumericUsers: Boolean, userNumber: Int?): String {
    val num = userNumber ?: return ""
    if (num !in 1..2) return ""
    return if (hasNumericUsers) num.toString() else if (num == 1) USER_A else USER_B
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
      ScaleSetupType.Bluetooth, ScaleSetupType.Lcbt, ScaleSetupType.BpmBluetooth, ScaleSetupType.BpmA6Bluetooth, ScaleSetupType.BabyScale -> AppIcons.Connection.Bluetooth
      ScaleSetupType.BtWifiR4 -> AppIcons.Connection.BluetoothWifi
      ScaleSetupType.AppSync -> AppIcons.Connection.AppSync
    }
}
