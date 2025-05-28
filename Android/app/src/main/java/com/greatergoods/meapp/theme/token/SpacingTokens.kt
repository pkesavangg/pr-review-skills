/**
 * Provides spacing tokens for the application's 8-point spacing system, as defined in the design system.
 *
 * Each property represents a semantic spacing size (xs, sm, md, lg, xl, 2xl, 3xl, 4xl, 5xl, 6xl),
 * ensuring consistent layout and spacing throughout the app.
 */
package com.greatergoods.meapp.theme.token

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.model.Spacing

val SpacingToken = Spacing(
    // Extra Small
    xs = 8.dp,
    // Small
    sm = 16.dp,
    // Medium
    md = 24.dp,
    // Large
    lg = 32.dp,
    // Extra Large
    xl = 40.dp,
    // 2XL
    x2l = 48.dp,
    // 3XL
    x3l = 56.dp,
    // 4XL
    x4l = 64.dp,
    // 5XL
    x5l = 72.dp,
    // 6XL
    x6l = 80.dp
)

val LocalSpacing = staticCompositionLocalOf<Spacing> {
    SpacingToken
}
