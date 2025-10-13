package com.dmdbrands.gurus.weight.features.addScale.strings

/**
 * Strings for the Add Scale screen and related dialogs/popups.
 */
object AddScaleScreenStrings {
  const val Header = "Add & Edit Scales"
  const val Title = "Add a Scale"
  const val Subtitle = "Enter the 4-digit model number found on the back of your scale."
  const val ModelNumberLabel = "model number"
  const val MyScales = "My Scales"
  const val Submit = "SUBMIT"
  const val CantFindModelNumber = "CAN'T FIND YOUR MODEL NUMBER?"
}

object ChooseScaleStrings {
  const val Header = "Choose Your Scale"
}

object PairedScaleExistsAlert {
  const val Title = "Device already paired"
  fun Message(deviceName: String) =
    "The device with sku: $deviceName is already paired. Do you want to pair it again?"
  const val Cancel = "Cancel"
  const val Pair = "Pair"
}
