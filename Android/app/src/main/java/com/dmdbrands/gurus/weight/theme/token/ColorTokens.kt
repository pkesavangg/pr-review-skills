/**
 * Defines semantic color tokens for the application's light and dark themes.
 *
 * Each token represents a semantic color role (background, support, action, text, icon, brand, etc.) as specified in the design system.
 * These tokens are used to construct the app's color schemes for Jetpack Compose, ensuring consistent theming.
 *
 * See the design reference for the mapping of each semantic role to palette values.
 */
package com.dmdbrands.gurus.weight.theme.token

import com.dmdbrands.gurus.weight.theme.enums.ColorPalette

/**
 * Semantic color tokens for the light theme, mapping design system roles to palette values.
 */
internal object LightColorToken {
  // Background
  val primary = ColorPalette.Neutral100 // #FFFFFF
  val primaryDisabled = ColorPalette.Neutral300 // 50% of #FFFFFF
  val secondary = ColorPalette.Neutral150
  val subSecondary = ColorPalette.Neutral200
  val tertiary = ColorPalette.Neutral230

  // Status
  val goal = ColorPalette.Green300 // #458239
  val success = ColorPalette.Green300 // #458239
  val secondarySuccess = ColorPalette.Green200
  val tertiarySuccess = ColorPalette.Green400
  val danger = ColorPalette.Red900 // #B3261E
  val streak = ColorPalette.Yellow200 // #EDB53A
  val utility = ColorPalette.Neutral400 // #D0CCCA
  val glow = ColorPalette.Neutral450 // 25% of #000000
  val baby = ColorPalette.Purple300 // #8841A4

  // Text
  val heading = ColorPalette.Neutral1000 // #2C2827
  val body = ColorPalette.Neutral1000 // #2C2827
  val subheading = ColorPalette.Neutral600 // #7B726E
  val textError = ColorPalette.Red900 // #B3261E
  val textErrorDisabled = ColorPalette.Red100 // #F5C0BD
  val textWarning = ColorPalette.Yellow200 // #EDB53A

  // Action
  val primaryFocusedAction = ColorPalette.Neutral1050 // #1F1C1B (primary-pressed)
  val primaryAction = ColorPalette.Neutral1000 // #2C2827 (primary-default)
  val primaryActionDisabled = ColorPalette.Neutral400 // #D0CCCA (primary-disabled)
  val secondaryAction = ColorPalette.Neutral100 // #FFFFFF (secondary-default)
  val secondaryActionDisabled = ColorPalette.Neutral400 // #D0CCCA
  val tertiaryAction = ColorPalette.Neutral600 // #7B726E
  val tertiaryActionDisabled = ColorPalette.Neutral400 // #D0CCCA
  val tertiaryActionSecondary = ColorPalette.Neutral770
  val inverse = ColorPalette.Neutral100 // #FFFFFF
  val inverseDisabled = ColorPalette.Neutral300 // 50% of #FFFFFF
  val inverseSecondary = ColorPalette.Neutral150 // #F6F4F1
  val errorAction = ColorPalette.Red900 // #B3261E
  val errorActionDisabled = ColorPalette.Red100 // #F5C0BD
  val errorActionSecondary = ColorPalette.Red800

  // Icon
  val iconPrimary = ColorPalette.Blue800 // #1565C0
  val iconPrimaryDisabled = ColorPalette.Blue500 // 50% of #1565C0 (icon-primary-disabled)
  val iconSecondary = ColorPalette.Neutral600 // #7B726E
  val iconSecondaryDisabled = ColorPalette.Neutral400 // #D0CCCA

  // Loading
  val loading = ColorPalette.Blue500 // 50% of #1565C0 (loading)
  val loadingError = ColorPalette.Red100 // #F5C0BD

  // Support
  val overlay = ColorPalette.Neutral500 // 25% of #2C2827
  val toastBackground = ColorPalette.Blue100 // #E3F2FD

  //iam
  val marketingPrimary = ColorPalette.Red150
  val marketingPrimaryAction = ColorPalette.Red700
  val marketingSecondary = ColorPalette.blue150
  val marketingSecondaryAction = ColorPalette.blue700
  val marketingTertiary = ColorPalette.green150
  val marketingTertiaryAction = ColorPalette.green900

  // Brand
  val meAppPrimary = ColorPalette.Teal100 // #65CEC8
  val wgPrimary = ColorPalette.Blue800 // #1565C0 (wg-primary)

  // Action - secondary pressed
  val secondaryFocusedAction = ColorPalette.Neutral1050 // #1F1C1B (secondary-pressed)
  // Status - utility secondary
  val utilitySecondary = ColorPalette.Neutral1000 // #2C2827 (utility-secondary)
  // Text - disabled & inverse
  val textDisabled = ColorPalette.Neutral400 // #D0CCCA (disabled-text)
  val textInverse = ColorPalette.Neutral100 // #FFFFFF (text inverse)
  // Logo
  val logoPrimary = ColorPalette.Neutral1000 // #2C2827 (logo-primary)
  val logoSecondary = ColorPalette.Neutral100 // #FFFFFF (logo-secondary)
  // Greater Goods / promo (default=800, pressed=900, disabled=200)
  val ggSecondaryAction = ColorPalette.Neutral200 // #424242 (gg-secondary-default)
  val ggSecondaryActionPressed = ColorPalette.GgSecondary900 // #2F2F2F (gg-secondary-pressed)
  val ggSecondaryActionDisabled = ColorPalette.GgSecondary200 // #A1A1A1 (gg-secondary-disabled)
  val promoRed = ColorPalette.Red700 // #B8584E (promo-red-default)
  val promoRedPressed = ColorPalette.PromoRed900 // #98483F (promo-red-pressed)
  val promoRedDisabled = ColorPalette.PromoRed200 // #D5A6A1 (promo-red-disabled)
  val promoBlue = ColorPalette.blue700 // #4E738A (promo-blue-default)
  val promoBluePressed = ColorPalette.PromoBlue900 // #3F5E70 (promo-blue-pressed)
  val promoBlueDisabled = ColorPalette.PromoBlue200 // #A7B9C3 (promo-blue-disabled)
  val promoGreen = ColorPalette.green900 // #6E796B (promo-green-default)
  val promoGreenPressed = ColorPalette.PromoGreen900 // #5B6358 (promo-green-pressed)
  val promoGreenDisabled = ColorPalette.PromoGreen200 // #B4BBB0 (promo-green-disabled)
}

/**
 * Semantic color tokens for the dark theme, mapping design system roles to palette values.
 */
internal object DarkColorToken {
  // Background
  val primary = ColorPalette.Neutral900 // #222D39
  val primaryDisabled = ColorPalette.Neutral850 // 50% of #222D39
  val secondary = ColorPalette.Neutral950 // #12161B
  val subSecondary = ColorPalette.Neutral250
  val tertiary = ColorPalette.Neutral270

  // Status
  val goal = ColorPalette.Green800
  val success = ColorPalette.Green800
  val secondarySuccess = ColorPalette.Green100
  val tertiarySuccess = ColorPalette.Green850
  val danger = ColorPalette.Red500 // #F28B82
  val streak = ColorPalette.Yellow100 // #FDD663
  val utility = ColorPalette.Neutral800 // #565F68
  val glow = ColorPalette.Neutral550 // 25% of #FFFFFF
  val baby = ColorPalette.Purple300 // #8841A4

  // Text
  val heading = ColorPalette.Neutral700 // #E0E1E1
  val body = ColorPalette.Neutral700 // #E0E1E1
  val subheading = ColorPalette.Neutral750 // #92989F
  val textError = ColorPalette.Red500 // #F28B82
  val textErrorDisabled = ColorPalette.Red950 // #5C1A16
  val textWarning = ColorPalette.Yellow100 // #FDD663

  // Action
  val primaryFocusedAction = ColorPalette.Neutral130 // #F2F3F3 (primary-pressed)
  val primaryAction = ColorPalette.Neutral700 // #E0E1E1 (primary-default)
  val primaryActionDisabled = ColorPalette.Neutral800 // #565F68 (primary-disabled)
  val secondaryAction = ColorPalette.Neutral900 // #222D39 (secondary-default)
  val secondaryActionDisabled = ColorPalette.Neutral800 // #565F68
  val tertiaryAction = ColorPalette.Neutral750 // #92989F
  val tertiaryActionDisabled = ColorPalette.Neutral800 // #565F68
  val tertiaryActionSecondary = ColorPalette.Neutral780
  val inverse = ColorPalette.Neutral900 // #222D39
  val inverseDisabled = ColorPalette.Neutral850 // #50% of #222D39
  val inverseSecondary = ColorPalette.Neutral950 // #12161B
  val errorAction = ColorPalette.Red500 // #F28B82
  val errorActionDisabled = ColorPalette.Red950 // #5C1A16
  val errorActionSecondary = ColorPalette.Red850

  // Icon
  val iconPrimary = ColorPalette.Blue400 // #2B8AEB
  val iconPrimaryDisabled = ColorPalette.Blue600 // 60% of #2B8AEB (icon-primary-disabled)
  val iconSecondary = ColorPalette.Neutral750 // #92989F
  val iconSecondaryDisabled = ColorPalette.Neutral800 // #565F68

  // Loading
  val loading = ColorPalette.Blue600 // 60% of #2B8AEB (loading)
  val loadingError = ColorPalette.Red950 // #5C1A16

  // Support
  val overlay = ColorPalette.Neutral650 // 25% of #E0E1E1
  val toastBackground = ColorPalette.Blue1000 // #1A3959

  //iam
  val marketingPrimary = ColorPalette.Red200
  val marketingPrimaryAction = ColorPalette.Red750
  val marketingSecondary = ColorPalette.blue250
  val marketingSecondaryAction = ColorPalette.blue750
  val marketingTertiary = ColorPalette.green250
  val marketingTertiaryAction = ColorPalette.green950

  // Brand
  val meAppPrimary = ColorPalette.Teal200 // #00B3A6
  val wgPrimary = ColorPalette.Blue400 // #2B8AEB (wg-primary)

  // Action - secondary pressed
  val secondaryFocusedAction = ColorPalette.Neutral130 // #F2F3F3 (secondary-pressed)
  // Status - utility secondary
  val utilitySecondary = ColorPalette.Neutral700 // #E0E1E1 (utility-secondary)
  // Text - disabled & inverse
  val textDisabled = ColorPalette.Neutral800 // #565F68 (disabled-text)
  val textInverse = ColorPalette.Neutral900 // #222D39 (text inverse)
  // Logo
  val logoPrimary = ColorPalette.Neutral700 // #E0E1E1 (logo-primary)
  val logoSecondary = ColorPalette.Neutral900 // #222D39 (logo-secondary)
  // Greater Goods / promo (default=800, pressed=900, disabled=200)
  val ggSecondaryAction = ColorPalette.Neutral250 // #FCF8F4 (gg-secondary-default)
  val ggSecondaryActionPressed = ColorPalette.GgSecondary900Dark // #E8E3DE (gg-secondary-pressed)
  val ggSecondaryActionDisabled = ColorPalette.GgSecondary200Dark // #BFBAB6 (gg-secondary-disabled)
  val promoRed = ColorPalette.Red750 // #D9675C (promo-red-default)
  val promoRedPressed = ColorPalette.PromoRed900Dark // #E3847B (promo-red-pressed)
  val promoRedDisabled = ColorPalette.PromoRed200Dark // #854640 (promo-red-disabled)
  val promoBlue = ColorPalette.blue750 // #839DAD (promo-blue-default)
  val promoBluePressed = ColorPalette.PromoBlue900Dark // #9AB4C3 (promo-blue-pressed)
  val promoBlueDisabled = ColorPalette.PromoBlue200Dark // #596A76 (promo-blue-disabled)
  val promoGreen = ColorPalette.green950 // #9DAD99 (promo-green-default)
  val promoGreenPressed = ColorPalette.PromoGreen900Dark // #B3C4AF (promo-green-pressed)
  val promoGreenDisabled = ColorPalette.PromoGreen200Dark // #6B7768 (promo-green-disabled)
}
