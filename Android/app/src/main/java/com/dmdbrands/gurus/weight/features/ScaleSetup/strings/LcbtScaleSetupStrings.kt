package com.dmdbrands.gurus.weight.features.ScaleSetup.strings

import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState

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
    val Subtitle = "Next time you weigh in, just open Weight Gurus and step on the scale."
  }
}

