package com.greatergoods.meapp.theme.token

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.model.BorderRadius

/**
 * Provides border radius tokens for the application's 8-point spacing system, as defined in the design system.
 *
 * Each property represents a semantic border radius size (xs, sm, md, lg, xl, 2xl, 3xl, 4xl, 5xl, 6xl),
 * ensuring consistent border radius throughout the app.
 */
val BorderRadiusToken = BorderRadius(
    xs = 4.dp,  // Extra Small
    sm = 8.dp,  // Small
    md = 12.dp, // Medium
    lg = 16.dp, // Large
    xl = 28.dp, // Extra Large
    x2l = 44.dp, // 2XL
    pill = 999.dp, // pill
)

val LocalBorderRadius = staticCompositionLocalOf<BorderRadius> {
    BorderRadiusToken
} 