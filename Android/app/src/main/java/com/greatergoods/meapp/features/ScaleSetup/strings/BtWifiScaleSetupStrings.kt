package com.greatergoods.meapp.features.ScaleSetup.strings

import com.greatergoods.meapp.features.common.components.ConnectionState

object BtWifiScaleSetupStrings {
  object WakeupScale {
    fun Title(connectionState: ConnectionState) = if (connectionState == ConnectionState.Error) "Connection Error"
    else "Connecting to Bluetooth"

    fun Subtitle(connectionState: ConnectionState) = if (connectionState == ConnectionState.Error) null
    else "Give it a little tap, so your phone can find it."
  }

  object ConnectingBluetooth {
    fun Title(connectionState: ConnectionState) = if (connectionState == ConnectionState.Error) "Connection Error"
    else "Connecting to Bluetooth"
  }

  object GatheringNetwork {
    fun Title(connectionState: ConnectionState) = if (connectionState == ConnectionState.Error) "No Networks Found"
    else "Gathering Networks"
  }

  object ConnectingWifi {
    fun Title(connectionState: ConnectionState) = if (connectionState == ConnectionState.Error) "Connection Error"
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
}
