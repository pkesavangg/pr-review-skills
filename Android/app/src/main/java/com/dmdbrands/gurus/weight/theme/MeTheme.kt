package com.dmdbrands.gurus.weight.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import com.greatergoods.ggInAppMessaging.theme.LocalIamTypography
import com.greatergoods.ggInAppMessaging.theme.model.IamTypography
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources

val LocalAppTheme =
  staticCompositionLocalOf<ThemeMode> {
    ThemeMode.SYSTEM
  }

private const val MAX_FONT_SCALE = 1.3f

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
 * Resolves whether the dark color scheme should be used.
 *
 * For LIGHT/DARK the user's explicit choice short-circuits any Configuration
 * read, so Compose surfaces flip immediately on toggle regardless of whether
 * AppCompat / UiModeManager has propagated the change to Configuration yet.
 *
 * For SYSTEM, we read isSystemInDarkTheme() — which observes LocalConfiguration
 * and recomposes when the OS theme changes. This is reliable across API
 * levels because OS-driven Configuration updates (the user changing the
 * system theme) are broadcast normally; the runtime unreliability we hit on
 * API < 31 is specifically with app-driven setDefaultNightMode overrides,
 * which only matter when the user picks LIGHT or DARK.
 */
@Composable
private fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
  ThemeMode.DARK -> true
  ThemeMode.LIGHT -> false
  ThemeMode.SYSTEM, ThemeMode.UNRECOGNIZED -> isSystemInDarkTheme()
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
  val darkTheme = resolveDarkTheme(themeMode)

  val meAppColorScheme =
    if (darkTheme) {
      DarkColorScheme
    } else {
      LightColorScheme
    }

  // Apply status bar theming
  StatusBarTheme(meAppColorScheme)

  val systemDensity = LocalDensity.current
  val cappedDensity =
    remember(systemDensity) {
      if (systemDensity.fontScale > MAX_FONT_SCALE) {
        Density(density = systemDensity.density, fontScale = MAX_FONT_SCALE)
      } else {
        systemDensity
      }
    }

  // Override Configuration.uiMode so painterResource() and other resource lookups
  // resolve drawable-night/, values-night/, raw-night/ against the user's pick
  // instead of the OS-level uiMode. Without this, multi-color icons like
  // ic_settings_selected (selected bottom-nav tab) stay stuck on the OS theme
  // when the user switches in-app Appearance — see MA-3996.
  val baseContext = LocalContext.current
  val configuration = LocalConfiguration.current
  val themedContext = remember(baseContext, configuration, darkTheme) {
    val nightFlag = if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    // Rebuild from the live LocalConfiguration (not baseContext's snapshot) and flip only the
    // night bit, so screenWidthDp/fontScale/locale/orientation still propagate on config changes.
    val overridden = Configuration(configuration).apply {
      uiMode = applyNightFlag(uiMode, nightFlag)
    }
    // Wrap with ContextWrapper so the Activity remains discoverable via
    // getBaseContext() — required by hiltViewModel() and other Activity-context
    // lookups. Plain createConfigurationContext() returns a ContextImpl with no
    // Activity in its base chain and breaks Hilt.
    ThemedContextWrapper(baseContext, baseContext.createConfigurationContext(overridden).resources)
  }

  CompositionLocalProvider(
    LocalAppTheme provides themeMode,
    LocalColorScheme provides meAppColorScheme,
    LocalContext provides themedContext,
    LocalConfiguration provides themedContext.resources.configuration,
    LocalDensity provides cappedDensity,
    LocalTypography provides AppTypography,
    LocalSpacing provides SpacingToken,
    LocalAnimation provides AnimationToken,
    LocalBorderRadius provides BorderRadiusToken,
    LocalIamColors provides iamColorsFromColorScheme(meAppColorScheme),
    LocalIamTypography provides IAMTypography(AppTypography),
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
    subSecondaryBackground = colorScheme.subSecondaryBackground,
    tertiaryBackground = colorScheme.tertiaryBackground,

    // Action
    primaryFocusedAction = colorScheme.primaryFocusedAction,
    primaryAction = colorScheme.primaryAction,
    primaryActionDisabled = colorScheme.primaryActionDisabled,
    // IAM's secondaryAction is the dark filled-button colour (its only use is the TertiaryFilled
    // button bg). MeApp's secondaryAction went white in MOB-987, which made that button
    // white-on-white; map it to the neutral primaryAction (#2C2827) so it stays visible. (MOB-1261)
    secondaryAction = colorScheme.primaryAction,
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

    //iam
    marketingPrimary = colorScheme.marketingPrimary,
    marketingPrimaryAction = colorScheme.marketingPrimaryAction,
    marketingSecondary = colorScheme.marketingSecondary,
    marketingSecondaryAction = colorScheme.marketingSecondaryAction,
    marketingTertiary = colorScheme.marketingTertiary,
    marketingTertiaryAction = colorScheme.marketingTertiaryAction,
  )
}

private fun IAMTypography(typography: Typography): IamTypography {
  return IamTypography(
    typography.heading1,
    typography.heading2,
    typography.heading3,
    typography.heading4,
    typography.heading5,
    typography.heading6,
    typography.subHeading1,
    typography.subHeading2,
    typography.body1,
    typography.body2,
    typography.body3,
    typography.body4,
    typography.body5,
    typography.link1,
    typography.link2,
    typography.button1,
    typography.button2,
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

/**
 * Context wrapper that returns an override Resources (with adjusted Configuration.uiMode)
 * while keeping the original Activity discoverable via base context. This is required
 * because `hiltViewModel()` and related APIs walk getBaseContext() looking for an
 * Activity — `createConfigurationContext()` alone returns a ContextImpl that breaks
 * that lookup. See MA-3996.
 */
private class ThemedContextWrapper(
  base: Context,
  private val themedResources: Resources,
) : ContextWrapper(base) {
  override fun getResources(): Resources = themedResources
}
