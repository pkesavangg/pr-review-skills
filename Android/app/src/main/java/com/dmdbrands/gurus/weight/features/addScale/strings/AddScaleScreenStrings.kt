package com.dmdbrands.gurus.weight.features.addScale.strings

object AddScaleScreenStrings {
  const val Header = "My Devices"
  const val Title = "Add a Device"
  const val Subtitle = "Enter the 4-digit model number found on the back of your device."
  const val ModelNumberLabel = "model number"
  const val MyDevices = "My Devices"
  const val Submit = "SUBMIT"
  const val CantFindModelNumber = "CAN'T FIND YOUR MODEL NUMBER?"

  // region Accessibility (TalkBack)
  /** Spoken label for the icon-only close button in the Add Scale app bar. */
  const val accCloseButton = "Close"
  // endregion
}

object ChooseScaleStrings {
  const val Header = "Choose your device"
}

object PairedScaleExistsAlert {
  const val Title = "Device already paired"
  fun Message(deviceName: String) =
    "The device with sku: $deviceName is already paired. Do you want to pair it again?"
  const val Cancel = "Cancel"
  const val Return = "Return"
  const val Pair = "Pair"
}
