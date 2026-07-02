package com.dmdbrands.gurus.weight.features.DeviceSetup.strings

import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper

/**
 * Strings for Scale Setup screens.
 */
object DeviceSetupStrings {
  /** Default display name when scale product info is unknown. */
  const val UnknownScale = "Unknown Scale"

  fun Header(sku: String) = "Scale Setup – $sku"
  val BabyScaleHeader = "Scale Setup – ${DeviceHelper.SKU_0220}/${DeviceHelper.SKU_0222}"

  const val backButton = "back"
  const val nextButton = "next"
  const val FinishButton = "Finish"
  const val skipButton = "skip"
  const val saveButton = "save"
  const val SaveScaleLoader = "Saving..."

  object DeviceInfo {
    fun Title(sku: String) = "Model $sku"
    val BabyScaleTitle = "Model ${DeviceHelper.SKU_0220}/${DeviceHelper.SKU_0222}"
    const val WifiScaleButtonText = "Get your scale’s MAC address"
    const val MonitorSubtitle = "If you have any trouble setting up your monitor, you can connect with our team via the help button in the top right."
    const val Subtitle =
      "If you’re having trouble setting up your scale, press the help button in the top right to connect with our team."
    const val BabyScaleSubtitle =
      "If you have any trouble setting up your monitor, you can connect with our team via the help button in the top right."
  }

  object ExitSetupAlert {
    const val Title = "Are you sure you want to exit?"
    fun Message(isConnected: Boolean) = if (isConnected)
      "If you exit early, you may not be able to access some features until set up."
    else "The scale will not be connected."

    const val GoBack = "Go Back"
    const val Exit = "Exit"
    const val Return = "Return"
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
    const val Continue = "Continue"
    const val SetupWifiLater = "Setup Wifi Later"
  }

  object DevicePermissions {
    const val Title = "Permission Settings"
    fun Subtitle(setupType: DeviceSetupType) = when (setupType) {
      DeviceSetupType.AppSync -> "me.App needs Camera permission to Scan your scale."
      DeviceSetupType.Wifi,
      DeviceSetupType.EspTouchWifi -> "me.App needs Location permissions to connect to your scale."

      DeviceSetupType.BtWifiR4,
      DeviceSetupType.Bluetooth -> "me.App needs Bluetooth and Location permissions to connect to your scale."

      DeviceSetupType.Lcbt -> "me.App requires location access to view your Wi-Fi network information and connect to your scale."
      DeviceSetupType.BpmBluetooth,
      DeviceSetupType.BpmA6Bluetooth -> "me.App needs access to the following permissions to connect to your monitor."

      DeviceSetupType.BabyScale -> "meApp needs access to the following permissions to connect with your scale."
    }
  }

  object WifiList {
    const val ConnectedNetwork = "Connected Network"
    const val AvailableNetworks = "Available Networks"
    const val NoNetworks = "Unable to gather networks.."
  }

  object SkipWifiPermissions {
    const val Title = "Are you sure you want to skip?"
    const val Message = "Doing so makes necessary a more in-depth, time consuming setup process."
    const val Skip = "YES, SKIP"
    const val Goback = "GO BACK"
  }

  object SkipBtWifiPermissions {
    const val Title = "Are you sure you want to skip Wi-Fi?"
    const val Message = "After setup, find additional Wi-Fi settings or the MAC Address via scale settings."
    const val Skip = "SKIP"
    const val Back = "Back"
  }

  object PermissionAlerts {
    object LocationDisabled {
      const val Title = "Location Services Disabled"
      const val Message = "Please enable Location Services to continue with scale setup."
      const val Enable = "Enable"
    }

    object LocationAccessDisabled {
      const val Title = "Location Permission Required"
      const val Message = "Location permission is required to detect your WiFi network for scale setup."
      const val Enable = "Enable"
    }

    object InternetRequired {
      const val Title = "Internet Required"
      const val Message = "Internet required to connect Wi-Fi-scales"
    }
  }

  object WeightOnlyModeAlertDismiss {
    const val Title = "Are you sure?"
    const val Message =
      "The alert will be dismissed for this session. Visit scale settings to enable and/or review users"
    const val Dismiss = "Dismiss"
    const val Cancel = "Cancel"
  }

  // region Accessibility (TalkBack)
  /** Spoken label for the icon-only close button in the scale-setup app bar. */
  const val accCloseButton = "Close setup"

  /** Spoken label for the icon-only help button in the scale-setup app bar. */
  const val accHelpButton = "Help"

  /** Spoken description for the green check shown when a setup step is complete. */
  const val accSetupCompleteImage = "Setup complete"

  /** Spoken label for the Wi-Fi signal icon shown beside a network name. */
  const val accWifiNetworkIcon = "Wi-Fi network"
  // endregion
}
