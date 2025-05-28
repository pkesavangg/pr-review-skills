/**
 * Defines the application's spacing system for Jetpack Compose, based on the 8-point design system.
 *
 * Each property represents a semantic spacing size (xs, sm, md, lg, xl, 2xl, 3xl, 4xl, 5xl, 6xl),
 * ensuring consistent layout and spacing throughout the app.
 */
package com.greatergoods.meapp.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp

@Stable
data class Spacing(
    // Extra Small
    val xs: Dp,
    // Small
    val sm: Dp,
    // Medium
    val md: Dp,
    // Large
    val lg: Dp,
    // Extra Large
    val xl: Dp,
    // 2XL
    val x2l: Dp,
    // 3XL
    val x3l: Dp,
    // 4XL
    val x4l: Dp,
    // 5XL
    val x5l: Dp,
    // 6XL
    val x6l: Dp
)
