package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DEVICES
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.resources.AppIcons

/**
 * Helper object providing conversion functions for scale data.
 */
object DeviceDataHelper {
  private const val USER_A = "A"
  private const val USER_B = "B"

  /**
   * Finds [DeviceModelInfo] by SKU, mapping variant SKUs (e.g. 0022 -> 0383) for lookup.
   * @param sku original or variant SKU; `null` short-circuits to `null`.
   * @return matching [DeviceModelInfo], or `null` when [sku] is null or unknown.
   */
  fun findScaleInfoBySku(sku: String?): DeviceModelInfo? {
    val nonNullSku = sku ?: return null
    val lookupSku = DeviceHelper.mapSkuForDisplay(nonNullSku)
    val found = DEVICES.find { it.sku == lookupSku } ?: return null
    return if (nonNullSku != lookupSku) found.copy(sku = nonNullSku) else found
  }

  /**
   * Converts a GGDevice to DeviceModelInfo for UI display.
   */
  fun Device.toScaleInfo(): DeviceModelInfo {
    val setupType =
      when (this.deviceType?.lowercase()) {
        "wifi", "esptouchwifi" -> DeviceSetupType.Wifi
        "bluetooth", "lcbt" -> DeviceSetupType.Bluetooth
        "babyscale" -> DeviceSetupType.BabyScale
        "btwifir4" -> DeviceSetupType.BtWifiR4
        "appsync" -> DeviceSetupType.AppSync
        "bpmbluetooth" -> DeviceSetupType.BpmBluetooth
        "bpma6bluetooth" -> DeviceSetupType.BpmA6Bluetooth
        else -> DeviceSetupType.Bluetooth // Default fallback
      }

    // Get stored SKU and find scale info (maps 0022 -> 0383 internally)
    val storedSku = this.getSKU().orEmpty()
    val scaleInfoFromScales = findScaleInfoBySku(storedSku)
    val displaySku = if (scaleInfoFromScales != null) storedSku else DeviceHelper.mapSkuForDisplay(storedSku)
    val productName = scaleInfoFromScales?.productName ?: this.nickname
    val bodyComp = scaleInfoFromScales?.bodyComp ?: false

    return DeviceModelInfo(
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
  fun scaleTypeIcon(setupType: DeviceSetupType): Int =
    when (setupType) {
      DeviceSetupType.Wifi, DeviceSetupType.EspTouchWifi -> AppIcons.Connection.Wifi
      DeviceSetupType.Bluetooth, DeviceSetupType.Lcbt, DeviceSetupType.BpmBluetooth, DeviceSetupType.BpmA6Bluetooth, DeviceSetupType.BabyScale -> AppIcons.Connection.Bluetooth
      DeviceSetupType.BtWifiR4 -> AppIcons.Connection.BluetoothWifi
      DeviceSetupType.AppSync -> AppIcons.Connection.AppSync
    }
}
