package com.greatergoods.meapp.features.ScaleSetup.strings

import com.greatergoods.meapp.features.common.components.ConnectionState

object BtWifiScaleSetupStrings {
  object WakeupScale {
    fun Title(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) "Connection Error"
      else "Wake Your Scale"

    fun Subtitle(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) null
      else "Give it a little tap, so your phone can find it."
  }

  object ConnectingBluetooth {
    fun Title(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) "Connection Error"
      else "Connecting to Bluetooth"
  }

  object GatheringNetwork {
    fun Title(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) "No Networks Found"
      else "Gathering Networks"
  }

  object ConnectingWifi {
    fun Title(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) "Connection Error"
      else "Connecting to Wi-Fi"
  }

  object UpdateSettings {
    fun Title(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) "Update Failed"
      else "Updating Settings"
  }

  object CollectingMeasurement {
    fun Title(connectionState: ConnectionState) =
      if (connectionState == ConnectionState.Error) "Error Collecting Measurement"
      else "Collecting Measurement"
  }

  object ScaleConnected {
    const val Title = "Measurement Recorded"
    const val Subtitle = "Your measurement was verified and added to\n your account."
    const val WhatsThisButton = "What's This?"
  }

  object AccucheckModal {
    const val Title = "What is AccuCheck?"
    const val Messsage =
      "AccuCheck is Greater Goods’ proprietary algorithm, designed to provide the most accurate results possible every time you weigh in—down to .01 lb.\n\n" +
        "Shortly after getting on the scale, you’ll see an orange light. This means that you’ve been weighed. When you get off the scale, you’ll see a green light. This means that AccuCheck has double checked its work, and your weight has been verified. Essentially, AccuCheck is a second set of eyes, every time."
  }

  object WifiList {
    const val Title = "Select a 2.4 GHz Network"
    const val Subtitle =
      "If you have multiple Wi-Fi networks, pick the 2.4 GHz network closest to your scale."
    const val NoNetworks = "Unable to gather networks.."
  }

  object WifiPassword {
    const val Title = "Enter Wi-Fi Password"
    const val Subtitle = "Add the password for"
    const val PasswordLabel = "Password"
    const val NetworkPasswordToggleLabel = "Network has no password"
  }

  object CustomizeSettings {
    const val Title = "Customize your Settings"
    const val Subtitle = "You can update settings at any time."
  }

  object StepOn {
    const val Title = "One Last Step"
    const val Subtitle = "Step on the scale and take a measurement."
  }
}
