package com.greatergoods.meapp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.greatergoods.meapp.theme.model.Animation
import com.greatergoods.meapp.theme.model.ColorScheme
import com.greatergoods.meapp.theme.model.Spacing
import com.greatergoods.meapp.theme.model.Typography
import com.greatergoods.meapp.theme.token.AnimationToken
import com.greatergoods.meapp.theme.token.LocalAnimation
import com.greatergoods.meapp.theme.token.LocalSpacing
import com.greatergoods.meapp.theme.token.SpacingToken

/**
 * Main theme composable that sets up the app's theme.
 * This combines all theme components (colors, typography, spacing, animations) into a single theme.
 */
@Composable
fun MeAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val meAppColorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    CompositionLocalProvider(
        LocalColorScheme provides meAppColorScheme,
        LocalTypography provides AppTypography,
        LocalSpacing provides SpacingToken,
        LocalAnimation provides AnimationToken
    ) {
        MaterialTheme(
            content = content
        )
    }
}
