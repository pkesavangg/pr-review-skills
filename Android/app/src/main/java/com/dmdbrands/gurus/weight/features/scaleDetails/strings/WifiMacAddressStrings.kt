package com.dmdbrands.gurus.weight.features.scaleDetails.strings

object WifiMacAddressStrings {
  const val Header = "Wi-Fi MAC Address"
  const val Title = "The Wi-Fi MAC Address of your scale is:"
  const val CopyMacButton = "Copy MAC Address"
  const val MacEncryption = "##:##:##:##:##:##"
  const val MacAddressNote = "Record the address to share with your Wi-Fi network or IT department. " +
    "Once the scale is whitelisted, return to scale setup to pair your scale."

  object Toast {
    const val Success = "MAC Address copied to clipboard"
    const val Error = "Failed to copy MAC Address"
  }
}
