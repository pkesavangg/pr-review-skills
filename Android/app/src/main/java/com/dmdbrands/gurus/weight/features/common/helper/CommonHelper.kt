package com.dmdbrands.gurus.weight.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class DeviceType {
  Phone,
  Tablet,
  Fold
}

@Composable
fun getDeviceType(): DeviceType {
  val configuration = LocalConfiguration.current
  val widthDp = configuration.screenWidthDp
  val heightDp = configuration.screenHeightDp

  return when {
    // Folded outer display (short height, e.g. < 500dp)
    heightDp < 500 -> DeviceType.Fold
    // Normal phones
    widthDp < 600 -> DeviceType.Phone
    // Tablets
    else -> DeviceType.Tablet
  }
}
