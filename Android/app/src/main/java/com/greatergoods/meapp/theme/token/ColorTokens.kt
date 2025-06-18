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
    val primary = ColorPalette.Neutral100 // #FFFFFF
    val primaryDisabled = ColorPalette.Neutral300 // 50% of #FFFFFF
    val secondary = ColorPalette.Neutral150 // #F6F4F1

    // Status
    val goal = ColorPalette.Green200 // #458239
    val success = ColorPalette.Green200 // #458239
    val danger = ColorPalette.Red900 // #B3261E
    val streak = ColorPalette.Yellow200 // #EDB53A
    val utility = ColorPalette.Neutral400 // #D0CCCA

    // Text
    val heading = ColorPalette.Neutral1000 // #2C2827
    val body = ColorPalette.Neutral1000 // #2C2827
    val subheading = ColorPalette.Neutral600 // #7B726E
    val textError = ColorPalette.Red900 // #B3261E
    val textErrorDisabled = ColorPalette.Red100 // #F5C0BD

    // Action
    val primaryAction = ColorPalette.Blue900 // #1565C0
    val primaryActionDisabled = ColorPalette.Blue200 // #B8D6F4
    val secondaryAction = ColorPalette.Neutral1000 // #2C2827
    val secondaryActionDisabled = ColorPalette.Neutral400 // #D0CCCA
    val tertiaryAction = ColorPalette.Neutral600 // #7B726E
    val tertiaryActionDisabled = ColorPalette.Neutral400 // #D0CCCA
    val inverse = ColorPalette.Neutral100 // #FFFFFF
    val inverseDisabled = ColorPalette.Neutral300 // 50% of #FFFFFF
    val inverseSecondary = ColorPalette.Neutral150 // #F6F4F1

    // Icon
    val iconPrimary = ColorPalette.Blue900 // #1565C0
    val iconPrimaryDisabled = ColorPalette.Blue200 // #B8D6F4
    val iconSecondary = ColorPalette.Neutral600 // #7B726E
    val iconSecondaryDisabled = ColorPalette.Neutral400 // #D0CCCA

    // Loading
    val loading = ColorPalette.Blue200 // #B8D6F4
    val loadingError = ColorPalette.Red100 // #F5C0BD

    // Support
    val overlay = ColorPalette.Neutral500 // 50% of #2C2827
    val toastBackground = ColorPalette.Blue100 // #E3F2FD

    // Brand
    val meAppPrimary = ColorPalette.Teal100 // #65CEC8
    val wgPrimary = ColorPalette.Blue900 // #1565C0
}

/**
 * Semantic color tokens for the dark theme, mapping design system roles to palette values.
 */
internal object DarkColorToken {
    // Background
    val primary = ColorPalette.Neutral900 // #222D39
    val primaryDisabled = ColorPalette.Neutral850 // 50% of #222D39
    val secondary = ColorPalette.Neutral950 // #12161B

    // Status
    val goal = ColorPalette.Green100 // #63B453
    val success = ColorPalette.Green100 // #63B453
    val danger = ColorPalette.Red500 // #F28B82
    val streak = ColorPalette.Yellow100 // #FDD663
    val utility = ColorPalette.Neutral800 // #565F68

    // Text
    val heading = ColorPalette.Neutral700 // #E0E1E1
    val body = ColorPalette.Neutral700 // #E0E1E1
    val subheading = ColorPalette.Neutral750 // #92989F
    val textError = ColorPalette.Red500 // #F28B82
    val textErrorDisabled = ColorPalette.Red950 // #5C1A16

    // Action
    val primaryAction = ColorPalette.Blue400 // #2B8AEB
    val primaryActionDisabled = ColorPalette.Blue950 // 40% of #2B8AEB
    val secondaryAction = ColorPalette.Neutral700 // #E0E1E1
    val secondaryActionDisabled = ColorPalette.Neutral800 // #565F68
    val tertiaryAction = ColorPalette.Neutral750 // #92989F
    val tertiaryActionDisabled = ColorPalette.Neutral800 // #565F68
    val inverse = ColorPalette.Neutral900 // #222D39
    val inverseDisabled = ColorPalette.Neutral850 // #50% of #222D39
    val inverseSecondary = ColorPalette.Neutral950 // #12161B

    // Icon
    val iconPrimary = ColorPalette.Blue400 // #2B8AEB
    val iconPrimaryDisabled = ColorPalette.Blue950 // 40% of #2B8AEB
    val iconSecondary = ColorPalette.Neutral750 // #92989F
    val iconSecondaryDisabled = ColorPalette.Neutral800 // #565F68

    // Loading
    val loading = ColorPalette.Blue950 // 40% of #2B8AEB
    val loadingError = ColorPalette.Red950 // #5C1A16

    // Support
    val overlay = ColorPalette.Neutral650 // 50% of #E0E1E1
    val toastBackground = ColorPalette.Blue1000 // #1A3959

    // Brand
    val meAppPrimary = ColorPalette.Teal200 // #00B3A6
    val wgPrimary = ColorPalette.Blue900 // #1565C0
}
