package com.dmdbrands.gurus.weight.features.DeviceSetup.util

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo

/**
 * Utility class for scale setup navigation logic.
 * Contains functions to determine the appropriate setup route based on scale info.
 */
object DeviceSetupNavigationUtils {

  /**
   * Determines the WiFi setup type based on scale info, similar to TypeScript navigateToSetupPage.
   * @param scaleInfo The scale information containing setup type
   * @return The WiFi setup type string
   */
  fun determineWifiSetupType(scaleInfo: DeviceModelInfo): String {
    return when (scaleInfo.setupType) {
      DeviceSetupType.EspTouchWifi -> "espTouchWifi"
      DeviceSetupType.Wifi -> "first"
      else -> "first"
    }
  }

  /**
   * Determines the appropriate setup route based on scale info, similar to TypeScript navigateToSetupPage.
   * @param scaleInfo The scale information containing setup type
   * @return The appropriate AppRoute for the setup type
   */
  fun determineSetupRoute(scaleInfo: DeviceModelInfo): AppRoute {
    return when (scaleInfo.setupType) {
      DeviceSetupType.AppSync -> AppRoute.DeviceSetup.AppsyncScaleSetup(scaleInfo.sku)
      DeviceSetupType.Lcbt -> AppRoute.DeviceSetup.LcbtScaleSetup(scaleInfo.sku)
      DeviceSetupType.BabyScale -> AppRoute.DeviceSetup.BabyScaleSetup(scaleInfo.sku)
      DeviceSetupType.BtWifiR4 -> AppRoute.DeviceSetup.BtWifiScaleSetup(scaleInfo.sku)
      DeviceSetupType.Bluetooth -> AppRoute.DeviceSetup.BtScaleSetup(scaleInfo.sku)
      DeviceSetupType.EspTouchWifi -> {
        val wifiSetupType = determineWifiSetupType(scaleInfo)
        AppRoute.DeviceSetup.WifiScaleSetup(scaleInfo.sku, wifiSetupType, scaleInfo)
      }

      DeviceSetupType.Wifi -> {
        val wifiSetupType = determineWifiSetupType(scaleInfo)
        AppRoute.DeviceSetup.WifiScaleSetup(scaleInfo.sku, wifiSetupType, scaleInfo)
      }

      DeviceSetupType.BpmBluetooth, DeviceSetupType.BpmA6Bluetooth -> AppRoute.DeviceSetup.BpmSetup(scaleInfo.sku)
    }
  }

  /**
   * Creates a WiFi setup route with the appropriate setup type based on scale info.
   * This is the main entry point for WiFi scale setup navigation.
   * @param scaleInfo The scale information
   * @return The WiFi scale setup route with correct setup type
   */
  fun createWifiSetupRoute(scaleInfo: DeviceModelInfo): AppRoute.DeviceSetup.WifiScaleSetup {
    val wifiSetupType = determineWifiSetupType(scaleInfo)
    return AppRoute.DeviceSetup.WifiScaleSetup(scaleInfo.sku, wifiSetupType, scaleInfo)
  }
}
