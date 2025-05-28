package com.greatergoods.meapp.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.greatergoods.meapp.theme.model.Animation
import com.greatergoods.meapp.theme.model.ColorScheme
import com.greatergoods.meapp.theme.model.Spacing
import com.greatergoods.meapp.theme.model.Typography
import com.greatergoods.meapp.theme.token.LocalAnimation
import com.greatergoods.meapp.theme.token.LocalSpacing

/**
 * Object providing access to theme components throughout the app.
 */
object MeAppTheme {
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable get() = LocalColorScheme.current

    val typography: Typography
        @Composable @ReadOnlyComposable get() = LocalTypography.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val animation: Animation
        @Composable @ReadOnlyComposable get() = LocalAnimation.current
}