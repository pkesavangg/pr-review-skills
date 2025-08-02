package com.dmdbrands.gurus.weight.features.ScaleSetup.util

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo

/**
 * Utility class for scale setup navigation logic.
 * Contains functions to determine the appropriate setup route based on scale info.
 */
object ScaleSetupNavigationUtils {

  /**
   * Determines the WiFi setup type based on scale info, similar to TypeScript navigateToSetupPage.
   * @param scaleInfo The scale information containing setup type
   * @return The WiFi setup type string
   */
  fun determineWifiSetupType(scaleInfo: ScaleInfo): String {
    return when (scaleInfo.setupType) {
      ScaleSetupType.EspTouchWifi -> "espTouchWifi"
      ScaleSetupType.Wifi -> "first"
      else -> "first"
    }
  }

  /**
   * Determines the appropriate setup route based on scale info, similar to TypeScript navigateToSetupPage.
   * @param scaleInfo The scale information containing setup type
   * @return The appropriate AppRoute for the setup type
   */
  fun determineSetupRoute(scaleInfo: ScaleInfo): AppRoute {
    return when (scaleInfo.setupType) {
      ScaleSetupType.AppSync -> AppRoute.ScaleSetup.AppsyncScaleSetup(scaleInfo.sku)
      ScaleSetupType.Lcbt -> AppRoute.ScaleSetup.LcbtScaleSetup(scaleInfo.sku)
      ScaleSetupType.BtWifiR4 -> AppRoute.ScaleSetup.BtWifiScaleSetup(scaleInfo.sku)
      ScaleSetupType.Bluetooth -> AppRoute.ScaleSetup.BtScaleSetup(scaleInfo.sku)
      ScaleSetupType.EspTouchWifi -> {
        val wifiSetupType = determineWifiSetupType(scaleInfo)
        AppRoute.ScaleSetup.WifiScaleSetup(scaleInfo.sku, wifiSetupType, scaleInfo)
      }

      ScaleSetupType.Wifi -> {
        val wifiSetupType = determineWifiSetupType(scaleInfo)
        AppRoute.ScaleSetup.WifiScaleSetup(scaleInfo.sku, wifiSetupType, scaleInfo)
      }
    }
  }

  /**
   * Creates a WiFi setup route with the appropriate setup type based on scale info.
   * This is the main entry point for WiFi scale setup navigation.
   * @param scaleInfo The scale information
   * @return The WiFi scale setup route with correct setup type
   */
  fun createWifiSetupRoute(scaleInfo: ScaleInfo): AppRoute.ScaleSetup.WifiScaleSetup {
    val wifiSetupType = determineWifiSetupType(scaleInfo)
    return AppRoute.ScaleSetup.WifiScaleSetup(scaleInfo.sku, wifiSetupType, scaleInfo)
  }
}
