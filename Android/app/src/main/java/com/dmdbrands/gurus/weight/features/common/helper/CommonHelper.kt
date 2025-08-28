package com.dmdbrands.gurus.weight.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class DeviceType {
  Phone,
  Tablet
}

@Composable
fun getDeviceType(): DeviceType {
  val configuration = LocalConfiguration.current
  return if (configuration.screenWidthDp < 600) {
    DeviceType.Phone
  } else {
    DeviceType.Tablet
  }
}
