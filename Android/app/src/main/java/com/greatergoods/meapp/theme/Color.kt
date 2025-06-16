package com.greatergoods.meapp.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.greatergoods.meapp.theme.model.ColorScheme
import com.greatergoods.meapp.theme.token.DarkColorToken
import com.greatergoods.meapp.theme.token.LightColorToken

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
val LightColorScheme = ColorScheme(
    // Background
    primary = LightColorToken.primary,
    primaryDisabled = LightColorToken.primaryDisabled,
    secondary = LightColorToken.secondary,

    // Support
    overlay = LightColorToken.overlay,
    toastBackground = LightColorToken.toastBackground,

    // Action
    primaryAction = LightColorToken.primaryAction,
    primaryActionDisabled = LightColorToken.primaryActionDisabled,
    secondaryAction = LightColorToken.secondaryAction,
    secondaryActionDisabled = LightColorToken.secondaryActionDisabled,
    tertiaryAction = LightColorToken.tertiaryAction,
    tertiaryActionDisabled = LightColorToken.tertiaryActionDisabled,
    inverse = LightColorToken.inverse,
    inverseDisabled = LightColorToken.inverseDisabled,
    inverseSecondary = LightColorToken.inverseSecondary,

    // Status
    success = LightColorToken.success,
    error = LightColorToken.error,
    errorDisabled = LightColorToken.errorDisabled,
    streak = LightColorToken.streak,
    utility = LightColorToken.utility,
    iconPrimary = LightColorToken.iconPrimary,
    iconPrimaryDisabled = LightColorToken.iconPrimaryDisabled,
    iconSecondary = LightColorToken.iconSecondary,
    iconSecondaryDisabled = LightColorToken.iconSecondaryDisabled,
    loading = LightColorToken.loading,
    loadingError = LightColorToken.loadingError,

    // Text
    heading = LightColorToken.heading,
    body = LightColorToken.body,
    subheading = LightColorToken.subheading,
    textError = LightColorToken.textError,
    textErrorDisabled = LightColorToken.textErrorDisabled,

    // Brand
    meAppPrimary = LightColorToken.meAppPrimary,
    wgPrimary = LightColorToken.wgPrimary,
)

/**
 * Semantic color mapping for the dark theme.
 *
 * Maps each semantic color role (background, action, text, etc.) to the appropriate token from [DarkColorToken].
 * These tokens are derived from the design system's color palette (see reference image).
 */
val DarkColorScheme = ColorScheme(
    // Background
    primary = DarkColorToken.primary,
    primaryDisabled = DarkColorToken.primaryDisabled,
    secondary = DarkColorToken.secondary,

    // Support
    overlay = DarkColorToken.overlay,
    toastBackground = DarkColorToken.toastBackground,

    // Action
    primaryAction = DarkColorToken.primaryAction,
    primaryActionDisabled = DarkColorToken.primaryActionDisabled,
    secondaryAction = DarkColorToken.secondaryAction,
    secondaryActionDisabled = DarkColorToken.secondaryActionDisabled,
    tertiaryAction = DarkColorToken.tertiaryAction,
    tertiaryActionDisabled = DarkColorToken.tertiaryActionDisabled,
    inverse = DarkColorToken.inverse,
    inverseDisabled = DarkColorToken.inverseDisabled,
    inverseSecondary = DarkColorToken.inverseSecondary,

    // Status
    success = DarkColorToken.success,
    error = DarkColorToken.error,
    errorDisabled = DarkColorToken.errorDisabled,
    streak = DarkColorToken.streak,
    utility = DarkColorToken.utility,
    iconPrimary = DarkColorToken.iconPrimary,
    iconPrimaryDisabled = DarkColorToken.iconPrimaryDisabled,
    iconSecondary = DarkColorToken.iconSecondary,
    iconSecondaryDisabled = DarkColorToken.iconSecondaryDisabled,
    loading = DarkColorToken.loading,
    loadingError = DarkColorToken.loadingError,

    // Text
    heading = DarkColorToken.heading,
    body = DarkColorToken.body,
    subheading = DarkColorToken.subheading,
    textError = DarkColorToken.textError,
    textErrorDisabled = DarkColorToken.textErrorDisabled,

    // Brand
    meAppPrimary = DarkColorToken.meAppPrimary,
    wgPrimary = DarkColorToken.wgPrimary,
)

/**
 * CompositionLocal for accessing the current [ColorScheme] instance in the Compose hierarchy.
 *
 * Use this to retrieve the current semantic color scheme (light or dark) throughout the UI.
 */
val LocalColorScheme = staticCompositionLocalOf<ColorScheme> { error("No AppColors provided") }
