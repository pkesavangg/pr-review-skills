package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dimension constants for [DeviceSetupLoader] and related setup loader UI.
 * Use these instead of hardcoded dp values for consistency and reuse.
 */
object DeviceSetupLoaderConstants {
  /** Spacer height above primary button when showing failed state with indicator only. */
  val FailedIndicatorOnlySpacerHeight: Dp = 160.dp

  /** Default width for setup GIF image. */
  val SetupGifImageWidth: Dp = 370.dp

  /** Default height for setup GIF image. */
  val SetupGifImageHeight: Dp = 211.dp
}
