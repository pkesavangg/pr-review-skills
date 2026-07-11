/**
 * Defines semantic color tokens for the application's light and dark themes.
 *
 * Each token represents a semantic color role (background, support, action, text, icon, brand, etc.) as specified in the design system.
 * These tokens are used to construct the app's color schemes for Jetpack Compose, ensuring consistent theming.
 *
 * Base tokens are referenced from [ColorPalette.Light] / [ColorPalette.Dark], whose names map 1:1 to
 * the Figma base-color scale (e.g. `neutral-800` → `neutral800`). See the design reference for the
 * mapping of each semantic role to base tokens.
 */
package com.dmdbrands.gurus.weight.theme.token

import com.dmdbrands.gurus.weight.theme.enums.ColorPalette

/**
 * Semantic color tokens for the light theme, mapping design system roles to Figma base tokens.
 */
internal object LightColorToken {
  // Background
  val primary = ColorPalette.Light.neutral100 // #FFFFFF
  val primaryDisabled = ColorPalette.Light.neutral300 // 50% of #FFFFFF
  val secondary = ColorPalette.Light.neutral200 // #F6F4F1
  val subSecondary = ColorPalette.Light.ggSecondary800 // #424242
  val tertiary = ColorPalette.Light.ggSecondary100 // 20% of #424242

  // Status
  val goal = ColorPalette.Light.green900 // #36682D
  val success = ColorPalette.Light.green900 // #36682D
  val secondarySuccess = ColorPalette.Light.green800 // #458239
  val tertiarySuccess = ColorPalette.Light.green100 // #B7C3B0
  val danger = ColorPalette.Light.red800 // #B3261E
  val streak = ColorPalette.Light.yellow100 // #EDB53A
  val utility = ColorPalette.Light.neutral400 // #D0CCCA
  val glow = ColorPalette.Light.neutral500 // 15% of #000000
  val baby = ColorPalette.babyScale // #8841A4

  // Text
  val heading = ColorPalette.Light.neutral800 // #2C2827
  val body = ColorPalette.Light.neutral800 // #2C2827
  val subheading = ColorPalette.Light.neutral700 // #7B726E
  val textError = ColorPalette.Light.red800 // #B3261E
  val textErrorDisabled = ColorPalette.Light.red100 // #F5C0BD
  val textWarning = ColorPalette.Light.orange100 // #FF5F15

  // Action
  val primaryFocusedAction = ColorPalette.Light.neutral900 // #1F1C1B (primary-pressed)
  val primaryAction = ColorPalette.Light.neutral800 // #2C2827 (primary-default)
  val primaryActionDisabled = ColorPalette.Light.neutral400 // #D0CCCA (primary-disabled)
  val secondaryAction = ColorPalette.Light.neutral100 // #FFFFFF (secondary-default)
  val secondaryActionDisabled = ColorPalette.Light.neutral400 // #D0CCCA
  val tertiaryAction = ColorPalette.Light.neutral700 // #7B726E
  val tertiaryActionDisabled = ColorPalette.Light.neutral400 // #D0CCCA
  val tertiaryActionSecondary = ColorPalette.Light.neutral750 // #5E5653 (tertiary-pressed)
  val inverse = ColorPalette.Light.neutral100 // #FFFFFF
  val inverseDisabled = ColorPalette.Light.neutral300 // 50% of #FFFFFF
  val inverseSecondary = ColorPalette.Light.neutral200 // #F6F4F1
  val errorAction = ColorPalette.Light.red800 // #B3261E
  val errorActionDisabled = ColorPalette.Light.red100 // #F5C0BD
  val errorActionSecondary = ColorPalette.Light.red900 // #8C1D18

  // Icon
  val iconPrimary = ColorPalette.Light.neutral800 // #2C2827 (icon-primary → neutral-800)
  val iconPrimaryDisabled = ColorPalette.Light.neutral400 // #D0CCCA (icon-primary-disabled → neutral-400)
  val iconSecondary = ColorPalette.Light.neutral700 // #7B726E
  val iconSecondaryDisabled = ColorPalette.Light.neutral400 // #D0CCCA

  // Loading
  val loading = ColorPalette.Light.neutral700 // #7B726E (loading → neutral-700)
  val loadingError = ColorPalette.Light.red100 // #F5C0BD

  // Support
  val overlay = ColorPalette.Light.neutral600 // 25% of #2C2827
  val toastBackground = ColorPalette.Light.blue100 // #E3F2FD

  //iam
  val marketingPrimary = ColorPalette.Light.promoRed100 // 20% of #B8584E
  val marketingPrimaryAction = ColorPalette.Light.promoRed800 // #B8584E
  val marketingSecondary = ColorPalette.Light.promoBlue100 // 20% of #4E738A
  val marketingSecondaryAction = ColorPalette.Light.promoBlue800 // #4E738A
  val marketingTertiary = ColorPalette.Light.promoGreen100 // 20% of #6E796B
  val marketingTertiaryAction = ColorPalette.Light.promoGreen800 // #6E796B

  // Brand
  val meAppPrimary = ColorPalette.Light.teal100 // #65CEC8
  val wgPrimary = ColorPalette.Light.blue800 // #1565C0 (wg-primary)

  // Action - secondary pressed
  val secondaryFocusedAction = ColorPalette.Light.neutral200 // #F6F4F1 (secondary-pressed → neutral-200)
  // Status - utility secondary
  val utilitySecondary = ColorPalette.Light.neutral800 // #2C2827 (utility-secondary)
  // Text - disabled & inverse
  val textDisabled = ColorPalette.Light.neutral400 // #D0CCCA (disabled-text)
  val textInverse = ColorPalette.Light.neutral100 // #FFFFFF (text inverse)
  // Logo
  val logoPrimary = ColorPalette.Light.neutral800 // #2C2827 (logo-primary)
  val logoSecondary = ColorPalette.Light.neutral100 // #FFFFFF (logo-secondary)
  // Greater Goods / promo (default=800, pressed=900, disabled=200)
  val ggSecondaryAction = ColorPalette.Light.ggSecondary800 // #424242 (gg-secondary-default)
  val ggSecondaryActionPressed = ColorPalette.Light.ggSecondary900 // #2F2F2F (gg-secondary-pressed)
  val ggSecondaryActionDisabled = ColorPalette.Light.ggSecondary200 // #A1A1A1 (gg-secondary-disabled)
  val promoRed = ColorPalette.Light.promoRed800 // #B8584E (promo-red-default)
  val promoRedPressed = ColorPalette.Light.promoRed900 // #98483F (promo-red-pressed)
  val promoRedDisabled = ColorPalette.Light.promoRed200 // #D5A6A1 (promo-red-disabled)
  val promoBlue = ColorPalette.Light.promoBlue800 // #4E738A (promo-blue-default)
  val promoBluePressed = ColorPalette.Light.promoBlue900 // #3F5E70 (promo-blue-pressed)
  val promoBlueDisabled = ColorPalette.Light.promoBlue200 // #A7B9C3 (promo-blue-disabled)
  val promoGreen = ColorPalette.Light.promoGreen800 // #6E796B (promo-green-default)
  val promoGreenPressed = ColorPalette.Light.promoGreen900 // #5B6358 (promo-green-pressed)
  val promoGreenDisabled = ColorPalette.Light.promoGreen200 // #B4BBB0 (promo-green-disabled)
}

/**
 * Semantic color tokens for the dark theme, mapping design system roles to Figma base tokens.
 */
internal object DarkColorToken {
  // Background
  val primary = ColorPalette.Dark.neutral100 // #222D39
  val primaryDisabled = ColorPalette.Dark.neutral300 // 50% of #222D39
  val secondary = ColorPalette.Dark.neutral200 // #12161B
  val subSecondary = ColorPalette.Dark.ggSecondary800 // #FCF8F4
  val tertiary = ColorPalette.Dark.ggSecondary100 // 20% of #FCF8F4

  // Status
  val goal = ColorPalette.Dark.green800 // #63B453
  val success = ColorPalette.Dark.green800 // #63B453
  val secondarySuccess = ColorPalette.Dark.green900 // #79C66A
  val tertiarySuccess = ColorPalette.Dark.green100 // #3C6F2F
  val danger = ColorPalette.Dark.red800 // #F28B82
  val streak = ColorPalette.Dark.yellow100 // #FDD663
  val utility = ColorPalette.Dark.neutral400 // #565F68
  val glow = ColorPalette.Dark.neutral500 // 15% of #FFFFFF
  val baby = ColorPalette.babyScale // #8841A4

  // Text
  val heading = ColorPalette.Dark.neutral800 // #E0E1E1
  val body = ColorPalette.Dark.neutral800 // #E0E1E1
  val subheading = ColorPalette.Dark.neutral700 // #92989F
  val textError = ColorPalette.Dark.red800 // #F28B82
  val textErrorDisabled = ColorPalette.Dark.red100 // #5C1A16
  val textWarning = ColorPalette.Dark.orange100 // #FF5F15

  // Action
  val primaryFocusedAction = ColorPalette.Dark.neutral900 // #F2F3F3 (primary-pressed)
  val primaryAction = ColorPalette.Dark.neutral800 // #E0E1E1 (primary-default)
  val primaryActionDisabled = ColorPalette.Dark.neutral400 // #565F68 (primary-disabled)
  val secondaryAction = ColorPalette.Dark.neutral100 // #222D39 (secondary-default)
  val secondaryActionDisabled = ColorPalette.Dark.neutral400 // #565F68
  val tertiaryAction = ColorPalette.Dark.neutral700 // #92989F
  val tertiaryActionDisabled = ColorPalette.Dark.neutral400 // #565F68
  val tertiaryActionSecondary = ColorPalette.Dark.neutral750 // #71767B (tertiary-pressed)
  val inverse = ColorPalette.Dark.neutral100 // #222D39
  val inverseDisabled = ColorPalette.Dark.neutral300 // 50% of #222D39
  val inverseSecondary = ColorPalette.Dark.neutral200 // #12161B
  val errorAction = ColorPalette.Dark.red800 // #F28B82
  val errorActionDisabled = ColorPalette.Dark.red100 // #5C1A16
  val errorActionSecondary = ColorPalette.Dark.red900 // #F6B1AA

  // Icon
  val iconPrimary = ColorPalette.Dark.neutral800 // #E0E1E1 (icon-primary → neutral-800)
  val iconPrimaryDisabled = ColorPalette.Dark.neutral400 // #565F68 (icon-primary-disabled → neutral-400)
  val iconSecondary = ColorPalette.Dark.neutral700 // #92989F
  val iconSecondaryDisabled = ColorPalette.Dark.neutral400 // #565F68

  // Loading
  val loading = ColorPalette.Dark.neutral700 // #92989F (loading → neutral-700)
  val loadingError = ColorPalette.Dark.red100 // #5C1A16

  // Support
  val overlay = ColorPalette.Dark.neutral600 // 25% of #E0E1E1
  val toastBackground = ColorPalette.Dark.blue100 // #1A3959

  //iam
  val marketingPrimary = ColorPalette.Dark.promoRed100 // 20% of #D9675C
  val marketingPrimaryAction = ColorPalette.Dark.promoRed800 // #D9675C
  val marketingSecondary = ColorPalette.Dark.promoBlue100 // 20% of #839DAD
  val marketingSecondaryAction = ColorPalette.Dark.promoBlue800 // #839DAD
  val marketingTertiary = ColorPalette.Dark.promoGreen100 // 20% of #9DAD99
  val marketingTertiaryAction = ColorPalette.Dark.promoGreen800 // #9DAD99

  // Brand
  val meAppPrimary = ColorPalette.Dark.teal100 // #00B3A6
  val wgPrimary = ColorPalette.Dark.blue800 // #2B8AEB (wg-primary)

  // Action - secondary pressed
  val secondaryFocusedAction = ColorPalette.Dark.neutral200 // #12161B (secondary-pressed → neutral-200)
  // Status - utility secondary
  val utilitySecondary = ColorPalette.Dark.neutral800 // #E0E1E1 (utility-secondary)
  // Text - disabled & inverse
  val textDisabled = ColorPalette.Dark.neutral400 // #565F68 (disabled-text)
  val textInverse = ColorPalette.Dark.neutral100 // #222D39 (text inverse)
  // Logo
  val logoPrimary = ColorPalette.Dark.neutral800 // #E0E1E1 (logo-primary)
  val logoSecondary = ColorPalette.Dark.neutral100 // #222D39 (logo-secondary)
  // Greater Goods / promo (default=800, pressed=900, disabled=200)
  val ggSecondaryAction = ColorPalette.Dark.ggSecondary800 // #FCF8F4 (gg-secondary-default)
  val ggSecondaryActionPressed = ColorPalette.Dark.ggSecondary900 // #E8E3DE (gg-secondary-pressed)
  val ggSecondaryActionDisabled = ColorPalette.Dark.ggSecondary200 // #BFBAB6 (gg-secondary-disabled)
  val promoRed = ColorPalette.Dark.promoRed800 // #D9675C (promo-red-default)
  val promoRedPressed = ColorPalette.Dark.promoRed900 // #E3847B (promo-red-pressed)
  val promoRedDisabled = ColorPalette.Dark.promoRed200 // #854640 (promo-red-disabled)
  val promoBlue = ColorPalette.Dark.promoBlue800 // #839DAD (promo-blue-default)
  val promoBluePressed = ColorPalette.Dark.promoBlue900 // #9AB4C3 (promo-blue-pressed)
  val promoBlueDisabled = ColorPalette.Dark.promoBlue200 // #596A76 (promo-blue-disabled)
  val promoGreen = ColorPalette.Dark.promoGreen800 // #9DAD99 (promo-green-default)
  val promoGreenPressed = ColorPalette.Dark.promoGreen900 // #B3C4AF (promo-green-pressed)
  val promoGreenDisabled = ColorPalette.Dark.promoGreen200 // #6B7768 (promo-green-disabled)
}
