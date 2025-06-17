package com.greatergoods.meapp.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.theme.enums.ColorSchemeKey

/**
 * Defines the semantic color roles for the application's theme, as per the design system reference image.
 *
 * Each property represents a semantic color role (background, text, support, action, icon, brand, etc.)
 * and is mapped to a palette value for use in Jetpack Compose theming.
 */
@Stable
data class ColorScheme(
    // Background
    val primary: Color,
    val primaryDisabled: Color,
    val secondary: Color,

    // Support
    val overlay: Color,
    val toastBackground: Color,

    // Action
    val primaryAction: Color,
    val primaryActionDisabled: Color,
    val secondaryAction: Color,
    val secondaryActionDisabled: Color,
    val tertiaryAction: Color,
    val tertiaryActionDisabled: Color,
    val inverse: Color,
    val inverseDisabled: Color,
    val inverseSecondary: Color,

    // Status
    val goal: Color,
    val success: Color,
    val danger: Color,
    val streak: Color,
    val utility: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val iconDisabled: Color,
    val loading: Color,
    val loadingError: Color,

    // Text
    val heading: Color,
    val body: Color,
    val subheading: Color,
    val textError: Color,
    val textErrorDisabled: Color,

    // Brand
    val meAppPrimary: Color,
    val wgPrimary: Color,
) {
    /**
     * Holds all semantic color roles for the app's theme.
     *
     * @property primary Primary background color (neutral-100/neutral-900)
     * @property primaryDisabled Disabled primary action color (blue-500/blue-975)
     * @property secondary Secondary background color (neutral-200/neutral-850)
     * @property overlay Overlay support color (neutral-500/neutral-650)
     * @property toastBackground Toast background support color (blue-100/blue-1000)
     * @property primaryAction Primary action color (blue-900/blue-950)
     * @property primaryDisabled Disabled state color (blue-500/blue-975)
     * @property secondaryAction Secondary action color (neutral-900/neutral-650)
     * @property secondaryDisabled Disabled secondary action color (neutral-400/neutral-600)
     * @property heading Heading text color (neutral-900/neutral-650)
     * @property body Body text color (neutral-900/neutral-650)
     * @property subheading Subheading text color (neutral-600)
     * @property error Error color (red-900/red-500)
     * @property errorDisabled Disabled error color (red-100/red-950)
     * @property inverse Inverse text color (neutral-100/neutral-800)
     * @property inverseSecondary Inverse secondary text color (neutral-200/neutral-850)
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
            ColorSchemeKey.Primary -> primary
            ColorSchemeKey.PrimaryDisabled -> primaryDisabled
            ColorSchemeKey.Secondary -> secondary

            // Support
            ColorSchemeKey.Overlay -> overlay
            ColorSchemeKey.ToastBackground -> toastBackground

            // Action
            ColorSchemeKey.PrimaryAction -> primaryAction
            ColorSchemeKey.PrimaryActionDisabled -> primaryActionDisabled
            ColorSchemeKey.SecondaryAction -> secondaryAction
            ColorSchemeKey.SecondaryActionDisabled -> secondaryActionDisabled
            ColorSchemeKey.TertiaryAction -> tertiaryAction
            ColorSchemeKey.TertiaryActionDisabled -> tertiaryActionDisabled
            ColorSchemeKey.Inverse -> inverse
            ColorSchemeKey.InverseDisabled -> inverseDisabled
            ColorSchemeKey.InverseSecondary -> inverseSecondary

            // Status
            ColorSchemeKey.Goal -> success
            ColorSchemeKey.Success -> success
            ColorSchemeKey.Danger -> danger
            ColorSchemeKey.Streak -> streak
            ColorSchemeKey.Utility -> utility
            ColorSchemeKey.IconPrimary -> iconPrimary
            ColorSchemeKey.IconSecondary -> iconSecondary
            ColorSchemeKey.IconDisabled -> iconDisabled
            ColorSchemeKey.Loading -> loading
            ColorSchemeKey.LoadingError -> loadingError

            // Text
            ColorSchemeKey.Heading -> heading
            ColorSchemeKey.Body -> body
            ColorSchemeKey.Subheading -> subheading
            ColorSchemeKey.Error -> textError
            ColorSchemeKey.ErrorDisabled -> textErrorDisabled

            // Brand
            ColorSchemeKey.MeAppPrimary -> meAppPrimary
            ColorSchemeKey.WgPrimary -> wgPrimary
        }
    }
}
