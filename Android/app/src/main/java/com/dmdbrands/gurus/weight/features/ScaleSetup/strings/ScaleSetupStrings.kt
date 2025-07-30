package com.dmdbrands.gurus.weight.features.ScaleSetup.strings

import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType

/**
 * Strings for Scale Setup screens.
 */
object ScaleSetupStrings {
  fun Header(sku: String) = "Scale Setup - $sku"

  const val backButton = "back"
  const val nextButton = "next"
  const val FinishButton = "Finish"
  const val skipButton = "skip"
  const val saveButton = "save"

  object ScaleInfo {
    fun Title(sku: String) = "Model $sku"
    const val WifiScaleButtonText = "Get your scale’s MAC address"
    const val Subtitle =
      "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right."
  }

  object ExitSetupAlert {
    const val Title = "Are you sure you want to exit?"
    fun Message(isConnected: Boolean) = if (isConnected)
      "If you exit early, you may not be able to \naccess some features until set up."
    else "The scale will not be connected."

    const val Back = "Back"
    const val Exit = "Exit"
  }

  object SetupButtons {
    const val TryAgain = "Try Again"
    const val Support = "Support"
    const val Finish = "Finish"
    const val Skip = "Skip"
    const val Save = "Save"
    const val Back = "Back"
    const val Refresh = "Refresh"
    const val Connect = "Connect"
    const val Next = "next"
    const val SomethingElse = "I see something else?"
    const val SetupWifiLater = "Setup Wifi Later"
  }

  object ScalePermissions {
    const val Title = "Permission Settings"
    fun Subtitle(setupType: ScaleSetupType) = when (setupType) {
      ScaleSetupType.AppSync -> "Weight Gurus needs Camera permission to Scan your scale."
      ScaleSetupType.Wifi,
      ScaleSetupType.EspTouchWifi -> "Weight Gurus needs Location permissions to connect with your scale."

      ScaleSetupType.BtWifiR4,
      ScaleSetupType.Bluetooth -> "Weight Gurus requires Bluetooth and Location permissions to connect with your scale."

      ScaleSetupType.Lcbt -> "Weight Gurus requires location access to view your Wi-Fi network information and connect to your scale."
    }
  }

  object WifiList {
    const val ConnectedNetwork = "Connected Network"
    const val AvailableNetworks = "Available Networks"
    const val NoNetworks = "Unable to gather networks.."
  }
}
