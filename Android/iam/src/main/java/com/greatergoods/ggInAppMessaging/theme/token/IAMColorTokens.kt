package com.greatergoods.ggInAppMessaging.theme.token

/**
 * Defines semantic color tokens for the IAM package's light and dark themes.
 *
 * Each token represents a semantic color role (background, support, action, text, icon, brand, etc.)
 * as specified in the IAM design system. These tokens are used to construct the IAM components'
 * color schemes for Jetpack Compose, ensuring consistent theming within the IAM package.
 *
 * The tokens follow the same semantic structure as the main app but are specifically tailored
 * for in-app messaging components and features.
 */

import com.greatergoods.ggInAppMessaging.theme.enums.IAMColorPalette

/**
 * Semantic color tokens for the IAM light theme, mapping design system roles to palette values.
 */
internal object IAMLightColorToken {
  // Background
  val primary = IAMColorPalette.IAMSurfaceLight // #FFFFFF
  val primaryDisabled = IAMColorPalette.IAMGray300 // 50% of #FFFFFF
  val secondary = IAMColorPalette.IAMBackgroundLight // #F6F4F1
  val tertiary = IAMColorPalette.IAMGray150 // #F6F4F1

  // Status
  val goal = IAMColorPalette.IAMGreen200 // #458239
  val success = IAMColorPalette.IAMGreen200 // #458239
  val danger = IAMColorPalette.IAMRed900 // #B3261E
  val warning = IAMColorPalette.IAMYellow200 // #EDB53A
  val utility = IAMColorPalette.IAMGray400 // #D0CCCA
  val glow = IAMColorPalette.IAMOverlayLight // 25% of #000000

  // Text
  val heading = IAMColorPalette.IAMTextPrimaryLight // #2C2827
  val body = IAMColorPalette.IAMTextPrimaryLight // #2C2827
  val subheading = IAMColorPalette.IAMTextSecondaryLight // #7B726E
  val textError = IAMColorPalette.IAMRed900 // #B3261E
  val textErrorDisabled = IAMColorPalette.IAMRed100 // #F5C0BD
  val textSuccess = IAMColorPalette.IAMGreen200 // #458239
  val textWarning = IAMColorPalette.IAMYellow200 // #EDB53A

  // Action
  val primaryAction = IAMColorPalette.IAMBlue900 // #1565C0
  val primaryActionDisabled = IAMColorPalette.IAMBlue500 // #B8D6F4
  val secondaryAction = IAMColorPalette.IAMTextPrimaryLight // #2C2827
  val secondaryActionDisabled = IAMColorPalette.IAMGray400 // #D0CCCA
  val tertiaryAction = IAMColorPalette.IAMTextSecondaryLight // #7B726E
  val tertiaryActionDisabled = IAMColorPalette.IAMGray400 // #D0CCCA
  val inverse = IAMColorPalette.IAMSurfaceLight // #FFFFFF
  val inverseDisabled = IAMColorPalette.IAMGray300 // 50% of #FFFFFF
  val inverseSecondary = IAMColorPalette.IAMBackgroundLight // #F6F4F1
  val errorAction = IAMColorPalette.IAMRed900 // #B3261E
  val errorActionDisabled = IAMColorPalette.IAMRed100 // #F5C0BD

  // Icon
  val iconPrimary = IAMColorPalette.IAMBlue900 // #1565C0
  val iconPrimaryDisabled = IAMColorPalette.IAMBlue200 // #B8D6F4
  val iconSecondary = IAMColorPalette.IAMTextSecondaryLight // #7B726E
  val iconSecondaryDisabled = IAMColorPalette.IAMGray400 // #D0CCCA
  val iconSuccess = IAMColorPalette.IAMGreen200 // #458239
  val iconWarning = IAMColorPalette.IAMYellow200 // #EDB53A
  val iconError = IAMColorPalette.IAMRed900 // #B3261E

  // Loading
  val loading = IAMColorPalette.IAMBlue200 // #B8D6F4
  val loadingError = IAMColorPalette.IAMRed100 // #F5C0BD
  val loadingSuccess = IAMColorPalette.IAMGreen100 // #63B453

  // Support
  val overlay = IAMColorPalette.IAMOverlayMedium // 50% of #2C2827
  val toastBackground = IAMColorPalette.IAMBlue100 // #E3F2FD
  val cardBackground = IAMColorPalette.IAMSurfaceLight // #FFFFFF
  val divider = IAMColorPalette.IAMGray400 // #D0CCCA

  // Brand
  val meAppPrimary = IAMColorPalette.IAMTeal100 // #65CEC8
  val wgPrimary = IAMColorPalette.IAMBlue900 // #1565C0

  // IAM-specific colors
  val promoCodeBackground = IAMColorPalette.IAMGray200 // #FCF8F4
  val promoCodeText = IAMColorPalette.IAMGray300 // #424242
  val copyButtonBackground = IAMColorPalette.IAMGray300 // #424242
  val copyButtonText = IAMColorPalette.IAMSurfaceLight // #FFFFFF

  // Theme-specific colors
  val themeRed = IAMColorPalette.IAMRed200 // #D9675C
  val themeGreen = IAMColorPalette.IAMGreen300 // #9DAD99
  val themeBlue = IAMColorPalette.IAMBlue300 // #4E738A
  val themeGray = IAMColorPalette.IAMGray300 // #424242
}

/**
 * Semantic color tokens for the IAM dark theme, mapping design system roles to palette values.
 */
internal object IAMDarkColorToken {
  // Background
  val primary = IAMColorPalette.IAMSurfaceDark // #222D39
  val primaryDisabled = IAMColorPalette.IAMGray850 // 50% of #222D39
  val secondary = IAMColorPalette.IAMBackgroundDark // #12161B
  val tertiary = IAMColorPalette.IAMGray950 // #12161B

  // Status
  val goal = IAMColorPalette.IAMGreen100 // #63B453
  val success = IAMColorPalette.IAMGreen100 // #63B453
  val danger = IAMColorPalette.IAMRed500 // #F28B82
  val warning = IAMColorPalette.IAMYellow100 // #FDD663
  val utility = IAMColorPalette.IAMGray800 // #565F68
  val glow = IAMColorPalette.IAMOverlayDark // 25% of #FFFFFF

  // Text
  val heading = IAMColorPalette.IAMTextPrimaryDark // #E0E1E1
  val body = IAMColorPalette.IAMTextPrimaryDark // #E0E1E1
  val subheading = IAMColorPalette.IAMTextSecondaryDark // #92989F
  val textError = IAMColorPalette.IAMRed500 // #F28B82
  val textErrorDisabled = IAMColorPalette.IAMRed950 // #5C1A16
  val textSuccess = IAMColorPalette.IAMGreen100 // #63B453
  val textWarning = IAMColorPalette.IAMYellow100 // #FDD663

  // Action
  val primaryAction = IAMColorPalette.IAMBlue400 // #2B8AEB
  val primaryActionDisabled = IAMColorPalette.IAMBlue600 // 40% of #2B8AEB
  val secondaryAction = IAMColorPalette.IAMTextPrimaryDark // #E0E1E1
  val secondaryActionDisabled = IAMColorPalette.IAMGray800 // #565F68
  val tertiaryAction = IAMColorPalette.IAMTextSecondaryDark // #92989F
  val tertiaryActionDisabled = IAMColorPalette.IAMGray800 // #565F68
  val inverse = IAMColorPalette.IAMSurfaceDark // #222D39
  val inverseDisabled = IAMColorPalette.IAMGray850 // 50% of #222D39
  val inverseSecondary = IAMColorPalette.IAMBackgroundDark // #12161B
  val errorAction = IAMColorPalette.IAMRed500 // #F28B82
  val errorActionDisabled = IAMColorPalette.IAMRed950 // #5C1A16

  // Icon
  val iconPrimary = IAMColorPalette.IAMBlue400 // #2B8AEB
  val iconPrimaryDisabled = IAMColorPalette.IAMBlue950 // 40% of #2B8AEB
  val iconSecondary = IAMColorPalette.IAMTextSecondaryDark // #92989F
  val iconSecondaryDisabled = IAMColorPalette.IAMGray800 // #565F68
  val iconSuccess = IAMColorPalette.IAMGreen100 // #63B453
  val iconWarning = IAMColorPalette.IAMYellow100 // #FDD663
  val iconError = IAMColorPalette.IAMRed500 // #F28B82

  // Loading
  val loading = IAMColorPalette.IAMBlue950 // 40% of #2B8AEB
  val loadingError = IAMColorPalette.IAMRed950 // #5C1A16
  val loadingSuccess = IAMColorPalette.IAMGreen200 // #458239

  // Support
  val overlay = IAMColorPalette.IAMOverlayMediumLight // 50% of #E0E1E1
  val toastBackground = IAMColorPalette.IAMBlue1000 // #1A3959
  val cardBackground = IAMColorPalette.IAMSurfaceDark // #222D39
  val divider = IAMColorPalette.IAMGray800 // #565F68

  // Brand
  val meAppPrimary = IAMColorPalette.IAMTeal200 // #00B3A6
  val wgPrimary = IAMColorPalette.IAMBlue400 // #2B8AEB

  // IAM-specific colors
  val promoCodeBackground = IAMColorPalette.IAMGray900 // #222D39
  val promoCodeText = IAMColorPalette.IAMGray700 // #E0E1E1
  val copyButtonBackground = IAMColorPalette.IAMGray700 // #E0E1E1
  val copyButtonText = IAMColorPalette.IAMSurfaceDark // #222D39

  // Theme-specific colors
  val themeRed = IAMColorPalette.IAMRed500 // #F28B82
  val themeGreen = IAMColorPalette.IAMGreen100 // #63B453
  val themeBlue = IAMColorPalette.IAMBlue400 // #2B8AEB
  val themeGray = IAMColorPalette.IAMGray700 // #E0E1E1
}
