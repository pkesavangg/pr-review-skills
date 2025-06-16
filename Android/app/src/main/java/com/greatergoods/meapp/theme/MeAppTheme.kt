package com.greatergoods.meapp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.theme.model.Animation
import com.greatergoods.meapp.theme.model.BorderRadius
import com.greatergoods.meapp.theme.model.ColorScheme
import com.greatergoods.meapp.theme.model.Spacing
import com.greatergoods.meapp.theme.model.Typography
import com.greatergoods.meapp.theme.token.AnimationToken
import com.greatergoods.meapp.theme.token.AppTypography
import com.greatergoods.meapp.theme.token.BorderRadiusToken
import com.greatergoods.meapp.theme.token.LocalAnimation
import com.greatergoods.meapp.theme.token.LocalBorderRadius
import com.greatergoods.meapp.theme.token.LocalSpacing
import com.greatergoods.meapp.theme.token.LocalTypography
import com.greatergoods.meapp.theme.token.SpacingToken
import android.util.Log

/**
 * Main theme composable that sets up the app's theme.
 * This combines all theme components (colors, typography, spacing, animations) into a single theme.
 */
@Composable
fun MeAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable (() -> Unit),
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.UNRECOGNIZED -> isSystemInDarkTheme()
        }

    val meAppColorScheme =
        if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        }

    CompositionLocalProvider(
        LocalColorScheme provides meAppColorScheme,
        LocalTypography provides AppTypography,
        LocalSpacing provides SpacingToken,
        LocalAnimation provides AnimationToken,
        LocalBorderRadius provides BorderRadiusToken,
    ) {
        MaterialTheme(
            content = content,
        )
    }
}

/**
 * Object providing access to theme components throughout the app.
 */
object MeAppTheme {
    // Color
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalColorScheme.current

    // Typography
    val typography: Typography
        @Composable @ReadOnlyComposable
        get() = LocalTypography.current

    // Spacing
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    // Animation
    val animation: Animation
        @Composable @ReadOnlyComposable
        get() = LocalAnimation.current

    // Border Radius
    val borderRadius: BorderRadius
        @Composable @ReadOnlyComposable
        get() = LocalBorderRadius.current
}
