package com.dmdbrands.gurus.weight.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.dmdbrands.gurus.weight.theme.model.ColorScheme
import com.dmdbrands.gurus.weight.theme.token.DarkColorToken
import com.dmdbrands.gurus.weight.theme.token.LightColorToken

/**
 * Defines the application's color schemes for Jetpack Compose theming, using semantic color roles.
 *
 * The color roles are based on the design system's semantic color mapping (see design reference image),
 * including background, text, support, action, icon, and brand colors. These are mapped to tokens for
 * both light and dark themes, ensuring consistent UI appearance and accessibility.
 *
 * - LightColors: Semantic color mapping for light mode.
 * - DarkColors: Semantic color mapping for dark mode.
 * - LocalColors: CompositionLocal for accessing the current Colors instance in the Compose hierarchy.
 *
 * Each color property corresponds to a semantic role (e.g., background, heading, error, brand, etc.)
 * as defined in the design system.
 */

/**
 * Semantic color mapping for the light theme.
 *
 * Maps each semantic color role (background, action, text, etc.) to the appropriate token from [LightColorToken].
 * These tokens are derived from the design system's color palette (see reference image).
 */
val LightColorScheme =
  ColorScheme(
    // Background
    primaryBackground = LightColorToken.primary,
    primaryBackgroundDisabled = LightColorToken.primaryDisabled,
    secondaryBackground = LightColorToken.secondary,
    subSecondaryBackground = LightColorToken.subSecondary,
    tertiaryBackground = LightColorToken.tertiary,
    // Support
    overlay = LightColorToken.overlay,
    toastBackground = LightColorToken.toastBackground,
    // Action
    primaryFocusedAction = LightColorToken.primaryFocusedAction,
    primaryAction = LightColorToken.primaryAction,
    primaryActionDisabled = LightColorToken.primaryActionDisabled,
    secondaryAction = LightColorToken.secondaryAction,
    secondaryActionDisabled = LightColorToken.secondaryActionDisabled,
    tertiaryAction = LightColorToken.tertiaryAction,
    tertiaryActionDisabled = LightColorToken.tertiaryActionDisabled,
    tertiaryActionSecondary = LightColorToken.tertiaryActionSecondary,
    inverseAction = LightColorToken.inverse,
    inverseActionDisabled = LightColorToken.inverseDisabled,
    inverseActionSecondary = LightColorToken.inverseSecondary,
    errorAction = LightColorToken.errorAction,
    errorActionDisabled = LightColorToken.errorActionDisabled,
    errorActionSecondary = LightColorToken.errorActionSecondary,
    // Status
    goal = LightColorToken.goal,
    success = LightColorToken.success,
    danger = LightColorToken.danger,
    streak = LightColorToken.streak,
    utility = LightColorToken.utility,
    glow = LightColorToken.glow,
    // Icon
    iconPrimary = LightColorToken.iconPrimary,
    iconPrimaryDisabled = LightColorToken.iconPrimaryDisabled,
    iconSecondary = LightColorToken.iconSecondary,
    iconSecondaryDisabled = LightColorToken.iconSecondaryDisabled,
    // Loading
    loading = LightColorToken.loading,
    loadingError = LightColorToken.loadingError,
    // Text
    textHeading = LightColorToken.heading,
    textBody = LightColorToken.body,
    textSubheading = LightColorToken.subheading,
    textError = LightColorToken.textError,
    textErrorDisabled = LightColorToken.textErrorDisabled,
    // Brand
    meAppPrimary = LightColorToken.meAppPrimary,
    wgPrimary = LightColorToken.wgPrimary,

    //iam
    marketingPrimary = LightColorToken.marketingPrimary,
    marketingPrimaryAction = LightColorToken.marketingPrimaryAction,
    marketingSecondary = LightColorToken.marketingSecondary,
    marketingSecondaryAction = LightColorToken.marketingSecondaryAction,
    marketingTertiary = LightColorToken.marketingTertiary,
    marketingTertiaryAction = LightColorToken.marketingTertiaryAction,
  )

/**
 * Semantic color mapping for the dark theme.
 *
 * Maps each semantic color role (background, action, text, etc.) to the appropriate token from [DarkColorToken].
 * These tokens are derived from the design system's color palette (see reference image).
 */
val DarkColorScheme =
  ColorScheme(
    // Background
    primaryBackground = DarkColorToken.primary,
    primaryBackgroundDisabled = DarkColorToken.primaryDisabled,
    secondaryBackground = DarkColorToken.secondary,
    subSecondaryBackground = DarkColorToken.subSecondary,
    tertiaryBackground = DarkColorToken.tertiary,
    // Support
    overlay = DarkColorToken.overlay,
    toastBackground = DarkColorToken.toastBackground,
    // Action
    primaryFocusedAction = DarkColorToken.primaryFocusedAction,
    primaryAction = DarkColorToken.primaryAction,
    primaryActionDisabled = DarkColorToken.primaryActionDisabled,
    secondaryAction = DarkColorToken.secondaryAction,
    secondaryActionDisabled = DarkColorToken.secondaryActionDisabled,
    tertiaryAction = DarkColorToken.tertiaryAction,
    tertiaryActionDisabled = DarkColorToken.tertiaryActionDisabled,
    tertiaryActionSecondary = DarkColorToken.tertiaryActionSecondary,
    inverseAction = DarkColorToken.inverse,
    inverseActionDisabled = DarkColorToken.inverseDisabled,
    inverseActionSecondary = DarkColorToken.inverseSecondary,
    errorAction = DarkColorToken.errorAction,
    errorActionDisabled = DarkColorToken.errorActionDisabled,
    errorActionSecondary = DarkColorToken.errorActionSecondary,
    // Status
    goal = DarkColorToken.goal,
    success = DarkColorToken.success,
    danger = DarkColorToken.danger,
    streak = DarkColorToken.streak,
    utility = DarkColorToken.utility,
    glow = DarkColorToken.glow,
    // Icon
    iconPrimary = DarkColorToken.iconPrimary,
    iconPrimaryDisabled = DarkColorToken.iconPrimaryDisabled,
    iconSecondary = DarkColorToken.iconSecondary,
    iconSecondaryDisabled = DarkColorToken.iconSecondaryDisabled,
    // Loading
    loading = DarkColorToken.loading,
    loadingError = DarkColorToken.loadingError,
    // Text
    textHeading = DarkColorToken.heading,
    textBody = DarkColorToken.body,
    textSubheading = DarkColorToken.subheading,
    textError = DarkColorToken.textError,
    textErrorDisabled = DarkColorToken.textErrorDisabled,
    // Brand
    meAppPrimary = DarkColorToken.meAppPrimary,
    wgPrimary = DarkColorToken.wgPrimary,
    //iam
    marketingPrimary = DarkColorToken.marketingPrimary,
    marketingPrimaryAction = DarkColorToken.marketingPrimaryAction,
    marketingSecondary = DarkColorToken.marketingSecondary,
    marketingSecondaryAction = DarkColorToken.marketingSecondaryAction,
    marketingTertiary = DarkColorToken.marketingTertiary,
    marketingTertiaryAction = DarkColorToken.marketingTertiaryAction,

  )

/**
 * CompositionLocal for accessing the current [ColorScheme] instance in the Compose hierarchy.
 *
 * Use this to retrieve the current semantic color scheme (light or dark) throughout the UI.
 */
val LocalColorScheme = staticCompositionLocalOf<ColorScheme> { error("No AppColors provided") }
