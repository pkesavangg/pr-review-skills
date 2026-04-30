/**
 * Defines the application's spacing system for Jetpack Compose, based on the 8-point design system.
 *
 * Each property represents a semantic spacing size (xs, sm, md, lg, xl, 2xl, 3xl, 4xl, 5xl, 6xl),
 * ensuring consistent layout and spacing throughout the app.
 */
package com.dmdbrands.gurus.weight.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class Spacing(
  val none: Dp = 0.dp,
  val x6s: Dp, // XXXXX Small
  val x4s: Dp, // XXXX Small
  val x3s: Dp, // XXX Small
  val x2s: Dp, // XX Small
  val xs: Dp, // Extra Small
  val sm: Dp, // Small
  val md: Dp, // Medium
  val lg: Dp, // Large
  val xl: Dp, // Extra Large
  val x2l: Dp, // 2XL
  val x3l: Dp, // 3XL
  val x4l: Dp, // 4XL
  val x5l: Dp, // 5XL
  val x6l: Dp, // 6XL
)
