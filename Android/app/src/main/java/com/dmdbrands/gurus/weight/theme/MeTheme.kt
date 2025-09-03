package com.dmdbrands.gurus.weight.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.theme.model.Animation
import com.dmdbrands.gurus.weight.theme.model.BorderRadius
import com.dmdbrands.gurus.weight.theme.model.ColorScheme
import com.dmdbrands.gurus.weight.theme.model.Spacing
import com.dmdbrands.gurus.weight.theme.model.Typography
import com.dmdbrands.gurus.weight.theme.token.AnimationToken
import com.dmdbrands.gurus.weight.theme.token.AppTypography
import com.dmdbrands.gurus.weight.theme.token.BorderRadiusToken
import com.dmdbrands.gurus.weight.theme.token.LocalAnimation
import com.dmdbrands.gurus.weight.theme.token.LocalBorderRadius
import com.dmdbrands.gurus.weight.theme.token.LocalSpacing
import com.dmdbrands.gurus.weight.theme.token.LocalTypography
import com.dmdbrands.gurus.weight.theme.token.SpacingToken
import com.greatergoods.ggInAppMessaging.theme.IamColors
import com.greatergoods.ggInAppMessaging.theme.LocalIamColors
import android.app.Activity

val LocalAppTheme =
  staticCompositionLocalOf<ThemeMode> {
    ThemeMode.SYSTEM
  }

/**
 * Composable that automatically sets the status bar colors based on the current theme.
 * This ensures the status bar matches the app's theme (light/dark mode).
 */
@Composable
private fun StatusBarTheme(colorScheme: ColorScheme) {
  val context = LocalContext.current

  SideEffect {
    val activity = context as? Activity
    val window = activity?.window

    window?.let {
      try {
        // Set status bar color to match the primary background
        it.statusBarColor = colorScheme.primaryBackground.toArgb()

        // Determine if we should use light or dark status bar content
        val isDarkTheme = colorScheme.primaryBackground.luminance() < 0.5f

        // Use WindowCompat for better compatibility across Android versions
        WindowCompat.getInsetsController(it, it.decorView).apply {
          isAppearanceLightStatusBars = !isDarkTheme
        }

        // For Android 6.0+ (API 23+), also set the status bar content color
        it.decorView.systemUiVisibility = if (isDarkTheme) {
          it.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
          it.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
      } catch (e: Exception) {
        // Fallback: use default system behavior if theming fails
        AppLog.w("Status bar ", "Failed to apply status bar theme${e}")
      }
    }
  }
}

/**
 * Main theme composable that sets up the app's theme.
 * This combines all theme components (colors, typography, spacing, animations) into a single theme.
 * It also provides IAM colors via LocalComposition for IAM components to access.
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

  // Apply status bar theming
  StatusBarTheme(meAppColorScheme)

  CompositionLocalProvider(
    LocalAppTheme provides themeMode,
    LocalColorScheme provides meAppColorScheme,
    LocalTypography provides AppTypography,
    LocalSpacing provides SpacingToken,
    LocalAnimation provides AnimationToken,
    LocalBorderRadius provides BorderRadiusToken,
    LocalIamColors provides iamColorsFromColorScheme(meAppColorScheme),
  ) {
    MaterialTheme(
      colorScheme = MaterialTheme.colorScheme.copy(
        primary = meAppColorScheme.primaryAction,           // Selection handles
        secondary = meAppColorScheme.primaryAction,         // Selection handles
        onPrimary = meAppColorScheme.textBody,             // Selected text color
        surfaceVariant = meAppColorScheme.toastBackground,
      ),
      content = content,
    )
  }
}

/**
 * Converts a ColorScheme to IamColors for IAM components.
 * This function takes the app's ColorScheme and maps it directly to IAM colors.
 */
private fun iamColorsFromColorScheme(colorScheme: ColorScheme): IamColors {
  return IamColors(
    // Background
    primaryBackground = colorScheme.primaryBackground,
    primaryBackgroundDisabled = colorScheme.primaryBackgroundDisabled,
    secondaryBackground = colorScheme.secondaryBackground,

    // Action
    primaryFocusedAction = colorScheme.primaryFocusedAction,
    primaryAction = colorScheme.primaryAction,
    primaryActionDisabled = colorScheme.primaryActionDisabled,
    secondaryAction = colorScheme.secondaryAction,
    secondaryActionDisabled = colorScheme.secondaryActionDisabled,
    tertiaryAction = colorScheme.tertiaryAction,
    tertiaryActionDisabled = colorScheme.tertiaryActionDisabled,
    tertiaryActionSecondary = colorScheme.tertiaryActionSecondary,
    inverseAction = colorScheme.inverseAction,
    inverseActionDisabled = colorScheme.inverseActionDisabled,
    inverseActionSecondary = colorScheme.inverseActionSecondary,
    errorAction = colorScheme.errorAction,
    errorActionDisabled = colorScheme.errorActionDisabled,
    errorActionSecondary = colorScheme.errorActionSecondary,

    // Status
    goal = colorScheme.goal,
    success = colorScheme.success,
    danger = colorScheme.danger,
    streak = colorScheme.streak,
    utility = colorScheme.utility,
    glow = colorScheme.glow,

    // Icon
    iconPrimary = colorScheme.iconPrimary,
    iconPrimaryDisabled = colorScheme.iconPrimaryDisabled,
    iconSecondary = colorScheme.iconSecondary,
    iconSecondaryDisabled = colorScheme.iconSecondaryDisabled,

    // Loading
    loading = colorScheme.loading,
    loadingError = colorScheme.loadingError,

    // Support
    overlay = colorScheme.overlay,
    toastBackground = colorScheme.toastBackground,

    // Text
    textHeading = colorScheme.textHeading,
    textBody = colorScheme.textBody,
    textSubheading = colorScheme.textSubheading,
    textError = colorScheme.textError,
    textErrorDisabled = colorScheme.textErrorDisabled,

    // Brand
    meAppPrimary = colorScheme.meAppPrimary,
    wgPrimary = colorScheme.wgPrimary,
  )
}

/**
 * Object providing access to theme components throughout the app.
 */
object MeTheme {
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
