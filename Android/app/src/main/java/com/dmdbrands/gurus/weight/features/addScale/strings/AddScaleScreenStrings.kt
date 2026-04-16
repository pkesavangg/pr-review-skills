package com.dmdbrands.gurus.weight.features.addScale.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Strings for the Add Scale screen and related dialogs/popups.
 */
object AddScaleScreenStrings {
  fun header(productType: ProductType) = when (productType) {
    ProductType.BLOOD_PRESSURE -> "Add & Edit Devices"
    else -> "Add & Edit Scales"
  }

  fun title(productType: ProductType) = when (productType) {
    ProductType.BLOOD_PRESSURE -> "Add a Device"
    else -> "Add a Scale"
  }

  fun subtitle(productType: ProductType) = when (productType) {
    ProductType.BLOOD_PRESSURE -> "Enter the 4-digit model number found on the back of your device."
    else -> "Enter the 4-digit model number found on the back of your scale."
  }

  const val ModelNumberLabel = "model number"

  fun myDevices(productType: ProductType) = when (productType) {
    ProductType.BLOOD_PRESSURE -> "My Devices"
    else -> "My Scales"
  }

  const val Submit = "SUBMIT"
  const val CantFindModelNumber = "CAN'T FIND YOUR MODEL NUMBER?"
}

object ChooseScaleStrings {
  fun header(productType: ProductType) = when (productType) {
    ProductType.BLOOD_PRESSURE -> "Choose your device"
    else -> "Choose your scale"
  }
}

object PairedScaleExistsAlert {
  const val Title = "Device already paired"
  fun Message(deviceName: String) =
    "The device with sku: $deviceName is already paired. Do you want to pair it again?"
  const val Cancel = "Cancel"
  const val Return = "Return"
  const val Pair = "Pair"
}
