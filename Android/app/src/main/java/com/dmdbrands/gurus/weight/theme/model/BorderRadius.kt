package com.dmdbrands.gurus.weight.theme.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Represents the border radius values used throughout the application.
 * These values follow the design system's border radius scale.
 */
data class BorderRadius(
    val none: Dp = 0.dp,
    val xs: Dp, // Extra Small
    val sm: Dp, // Small
    val md: Dp, // Medium
    val lg: Dp, // Large
    val xl: Dp, // Extra Large
    val x2l: Dp, // 2XL
    val pill: Dp // Full (circular)
)
