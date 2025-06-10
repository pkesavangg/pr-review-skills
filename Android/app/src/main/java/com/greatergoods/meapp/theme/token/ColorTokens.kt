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
    val secondary = ColorPalette.Neutral150

    // Support
    val overlay = ColorPalette.Neutral400
    val toastBackground = ColorPalette.Blue100

    // Action
    val primaryAction = ColorPalette.Blue900
    val primaryActionDisabled = ColorPalette.Blue500
    val secondaryAction = ColorPalette.Neutral900
    val secondaryDisabled = ColorPalette.Neutral300
    val tertiaryAction = ColorPalette.Neutral500
    val tertiaryDisabled = ColorPalette.Neutral300

    // Text
    val heading = ColorPalette.Neutral900
    val body = ColorPalette.Neutral900
    val subheading = ColorPalette.Neutral500
    val error = ColorPalette.Red900
    val errorDisabled = ColorPalette.Red100
    val inverse = ColorPalette.Neutral100
    val inverseDisabled = ColorPalette.Neutral200
    val inverseSecondary = ColorPalette.Neutral150

    // Icon
    val goal = ColorPalette.Green100
    val streak = ColorPalette.Yellow200
    val utility = ColorPalette.Neutral300

    // Brand
    val brand = ColorPalette.Teal100
}

/**
 * Semantic color tokens for the dark theme, mapping design system roles to palette values.
 */
internal object DarkColorToken {
    // Background
    val background = ColorPalette.Neutral800
    val secondary = ColorPalette.Neutral850

    // Support
    val overlay = ColorPalette.Neutral650
    val toastBackground = ColorPalette.Blue1000

    // Action
    val primaryAction = ColorPalette.Blue950
    val primaryActionDisabled = ColorPalette.Blue975
    val secondaryAction = ColorPalette.Neutral700
    val secondaryDisabled = ColorPalette.Neutral950
    val tertiaryAction = ColorPalette.Neutral600
    val tertiaryDisabled = ColorPalette.Neutral950


    // Text
    val heading = ColorPalette.Neutral650
    val body = ColorPalette.Neutral650
    val subheading = ColorPalette.Neutral600
    val error = ColorPalette.Red500
    val errorDisabled = ColorPalette.Red950
    val inverse = ColorPalette.Neutral800
    val inverseDisabled = ColorPalette.Neutral750
    val inverseSecondary = ColorPalette.Neutral850

    // Icon
    val goal = ColorPalette.Green200
    val streak = ColorPalette.Yellow100
    val utility = ColorPalette.Neutral600

    // Brand
    val brand = ColorPalette.Teal200
}
