package com.dmdbrands.gurus.weight.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.theme.enums.ColorSchemeKey

/**
 * Defines the semantic color roles for the application's theme, as per the design system reference image.
 *
 * Each property represents a semantic color role (background, text, support, action, icon, brand, etc.)
 * and is mapped to a palette value for use in Jetpack Compose theming.
 */
@Stable
data class ColorScheme(
  // Background
  val primaryBackground: Color,
  val primaryBackgroundDisabled: Color,
  val secondaryBackground: Color,
  val subSecondaryBackground: Color,
  val tertiaryBackground: Color,
  // Action
  val primaryFocusedAction: Color,
  val primaryAction: Color,
  val primaryActionDisabled: Color,
  val secondaryAction: Color,
  val secondaryActionDisabled: Color,
  val tertiaryAction: Color,
  val tertiaryActionDisabled: Color,
  val tertiaryActionSecondary: Color,
  val inverseAction: Color,
  val inverseActionDisabled: Color,
  val inverseActionSecondary: Color,
  val errorAction: Color,
  val errorActionDisabled: Color,
  val errorActionSecondary: Color,
  // Status
  val goal: Color,
  val success: Color,
  val secondarySuccess: Color,
  val tertiarySuccess: Color,
  val danger: Color,
  val streak: Color,
  val utility: Color,
  val glow: Color,
  val baby: Color,
  // Icon
  val iconPrimary: Color,
  val iconPrimaryDisabled: Color,
  val iconSecondary: Color,
  val iconSecondaryDisabled: Color,
  // Loading
  val loading: Color,
  val loadingError: Color,
  // Support
  val overlay: Color,
  val toastBackground: Color,
  // Text
  val textHeading: Color,
  val textBody: Color,
  val textSubheading: Color,
  val textError: Color,
  val textErrorDisabled: Color,
  // Advisory (non-blocking) warning text, e.g. out-of-typical-range manual entry.
  val textWarning: Color,
  // Brand
  val meAppPrimary: Color,
  val wgPrimary: Color,
  //iam
  val marketingPrimary: Color,
  val marketingPrimaryAction: Color,
  val marketingSecondary: Color,
  val marketingSecondaryAction: Color,
  val marketingTertiary: Color,
  val marketingTertiaryAction: Color,
) {
  /**
   * Holds all semantic color roles for the app's theme.
   *
   * @property primaryBackground Primary background color (neutral-100/neutral-900)
   * @property primaryBackgroundDisabled Disabled primary action color (blue-500/blue-975)
   * @property secondaryBackground Secondary background color (neutral-200/neutral-850)
   * @property overlay Overlay support color (neutral-500/neutral-650)
   * @property toastBackground Toast background support color (blue-100/blue-1000)
   * @property primaryAction Primary action color (blue-900/blue-950)
   * @property primaryBackgroundDisabled Disabled state color (blue-500/blue-975)
   * @property secondaryAction Secondary action color (neutral-900/neutral-650)
   * @property secondaryActionDisabled Disabled secondary action color (neutral-400/neutral-600)
   * @property textHeading Heading text color (neutral-900/neutral-650)
   * @property textBody Body text color (neutral-900/neutral-650)
   * @property textSubheading Subheading text color (neutral-600)
   * @property error Error color (red-900/red-500)
   * @property errorDisabled Disabled error color (red-100/red-950)
   * @property inverseAction Inverse text color (neutral-100/neutral-800)
   * @property inverseActionSecondary Inverse secondary text color (neutral-200/neutral-850)
   * @property goal Goal icon color (green-100/green-200)
   * @property streak Streak icon color (yellow-100/yellow-100)
   * @property utility Utility icon color (neutral-400/neutral-600)
   * @property brand Brand primary color (teal-100/teal-200)
   */
  fun fromToken(token: ColorSchemeKey): Color {
    /**
     * Returns the color associated with the given [ColorSchemeKey] semantic token.
     *
     * @param token The semantic color role key.
     * @return The corresponding color for the current theme.
     */
    return when (token) {
      // Background
      ColorSchemeKey.Primary -> primaryBackground
      ColorSchemeKey.PrimaryDisabled -> primaryBackgroundDisabled
      ColorSchemeKey.Secondary -> secondaryBackground

      // Support
      ColorSchemeKey.Overlay -> overlay
      ColorSchemeKey.ToastBackground -> toastBackground

      // Action
      ColorSchemeKey.PrimaryFocusedAction -> primaryFocusedAction
      ColorSchemeKey.PrimaryAction -> primaryAction
      ColorSchemeKey.PrimaryActionDisabled -> primaryActionDisabled
      ColorSchemeKey.SecondaryAction -> secondaryAction
      ColorSchemeKey.SecondaryActionDisabled -> secondaryActionDisabled
      ColorSchemeKey.TertiaryAction -> tertiaryAction
      ColorSchemeKey.TertiaryActionDisabled -> tertiaryActionDisabled
      ColorSchemeKey.TertiaryActionSecondary -> tertiaryActionSecondary
      ColorSchemeKey.Inverse -> inverseAction
      ColorSchemeKey.InverseDisabled -> inverseActionDisabled
      ColorSchemeKey.InverseSecondary -> inverseActionSecondary
      ColorSchemeKey.ErrorAction -> errorAction
      ColorSchemeKey.ErrorActionDisabled -> errorActionDisabled
      ColorSchemeKey.ErrorActionSecondary -> errorActionSecondary

      // Status
      ColorSchemeKey.Goal -> success
      ColorSchemeKey.Success -> success
      ColorSchemeKey.Danger -> danger
      ColorSchemeKey.Streak -> streak
      ColorSchemeKey.Utility -> utility
      ColorSchemeKey.Glow -> glow

      // Icon
      ColorSchemeKey.IconPrimary -> iconPrimary
      ColorSchemeKey.IconPrimaryDisabled -> iconPrimaryDisabled
      ColorSchemeKey.IconSecondary -> iconSecondary
      ColorSchemeKey.IconSecondaryDisabled -> iconSecondaryDisabled
      ColorSchemeKey.Loading -> loading
      ColorSchemeKey.LoadingError -> loadingError

      // Text
      ColorSchemeKey.Heading -> textHeading
      ColorSchemeKey.Body -> textBody
      ColorSchemeKey.Subheading -> textSubheading
      ColorSchemeKey.Error -> textError
      ColorSchemeKey.ErrorDisabled -> textErrorDisabled

      // Brand
      ColorSchemeKey.MeAppPrimary -> meAppPrimary
      ColorSchemeKey.WgPrimary -> wgPrimary
    }
  }
}
