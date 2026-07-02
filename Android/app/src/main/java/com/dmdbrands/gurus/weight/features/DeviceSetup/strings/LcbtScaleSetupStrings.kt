package com.dmdbrands.gurus.weight.features.DeviceSetup.strings

import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState

object LcbtScaleSetupStrings {
  object WakeupScale {
    fun Title(connectionState: ConnectionState) =
      if (connectionState is ConnectionState.Failed) "Connection Error"
      else "Wake Your Scale"

    fun Subtitle(connectionState: ConnectionState) =
      if (connectionState is ConnectionState.Failed) null
      else "Step on the scale, so your phone can find it."
  }

  object ConnectingBluetooth {
    fun Title(connectionState: ConnectionState) =
      if (connectionState is ConnectionState.Failed) "Connection Error"
      else "Connecting to Bluetooth"
  }

  object SetupFinished {
    val Title = "Your scale is paired and ready to go!"
    val Subtitle = "Next time you weigh in, the results will automatically be sent to me.App."
  }
}

