/**
 * Defines semantic color tokens for the application's light and dark themes.
 *
 * Each token represents a semantic color role (background, support, action, text, icon, brand, etc.) as specified in the design system.
 * These tokens are used to construct the app's color schemes for Jetpack Compose, ensuring consistent theming.
 *
 * See the design reference for the mapping of each semantic role to palette values.
 */
package com.greatergoods.meapp.theme.token

import com.greatergoods.meapp.theme.enums.ColorPalette

/**
 * Semantic color tokens for the light theme, mapping design system roles to palette values.
 */
internal object LightColorToken {
    // Background
    val primary = ColorPalette.Neutral100
    val primaryDisabled = ColorPalette.Neutral300
    val secondary = ColorPalette.Neutral150

    // Support
    val overlay = ColorPalette.Neutral500
    val toastBackground = ColorPalette.Blue100

    // Action
    val primaryAction = ColorPalette.Blue900
    val primaryActionDisabled = ColorPalette.Blue500
    val secondaryAction = ColorPalette.Neutral900
    val secondaryActionDisabled = ColorPalette.Neutral400
    val tertiaryAction = ColorPalette.Neutral600
    val tertiaryActionDisabled = ColorPalette.Neutral400
    val inverse = ColorPalette.Neutral100
    val inverseDisabled = ColorPalette.Neutral300
    val inverseSecondary = ColorPalette.Neutral150

    // Status
    val success = ColorPalette.Green100
    val error = ColorPalette.Red900
    val errorDisabled = ColorPalette.Red100
    val streak = ColorPalette.Yellow100
    val utility = ColorPalette.Neutral400
    val iconPrimary = ColorPalette.Blue900
    val iconPrimaryDisabled = ColorPalette.Blue500
    val iconSecondary = ColorPalette.Neutral600
    val iconSecondaryDisabled = ColorPalette.Neutral400
    val loading = ColorPalette.Blue500
    val loadingError = ColorPalette.Red100

    // Text
    val heading = ColorPalette.Neutral900
    val body = ColorPalette.Neutral900
    val subheading = ColorPalette.Neutral600
    val textError = ColorPalette.Red900
    val textErrorDisabled = ColorPalette.Red100

    // Brand
    val meAppPrimary = ColorPalette.Teal100
    val wgPrimary = ColorPalette.Blue900
}

/**
 * Semantic color tokens for the dark theme, mapping design system roles to palette values.
 */
internal object DarkColorToken {
    // Background
    val primary = ColorPalette.Neutral100
    val primaryDisabled = ColorPalette.Neutral300
    val secondary = ColorPalette.Neutral150

    // Support
    val overlay = ColorPalette.Neutral500
    val toastBackground = ColorPalette.Blue1000

    // Action
    val primaryAction = ColorPalette.Blue900
    val primaryActionDisabled = ColorPalette.Blue950
    val secondaryAction = ColorPalette.Neutral900
    val secondaryActionDisabled = ColorPalette.Neutral950
    val tertiaryAction = ColorPalette.Neutral600
    val tertiaryActionDisabled = ColorPalette.Neutral950
    val inverse = ColorPalette.Neutral100
    val inverseDisabled = ColorPalette.Neutral300
    val inverseSecondary = ColorPalette.Neutral150

    // Status
    val success = ColorPalette.Green200
    val error = ColorPalette.Red500
    val errorDisabled = ColorPalette.Red950
    val streak = ColorPalette.Yellow200
    val utility = ColorPalette.Neutral950
    val iconPrimary = ColorPalette.Blue900
    val iconPrimaryDisabled = ColorPalette.Blue950
    val iconSecondary = ColorPalette.Neutral600
    val iconSecondaryDisabled = ColorPalette.Neutral950
    val loading = ColorPalette.Blue950
    val loadingError = ColorPalette.Red950

    // Text
    val heading = ColorPalette.Neutral900
    val body = ColorPalette.Neutral900
    val subheading = ColorPalette.Neutral600
    val textError = ColorPalette.Red500
    val textErrorDisabled = ColorPalette.Red950

    // Brand
    val meAppPrimary = ColorPalette.Teal200
    val wgPrimary = ColorPalette.Blue900
}
