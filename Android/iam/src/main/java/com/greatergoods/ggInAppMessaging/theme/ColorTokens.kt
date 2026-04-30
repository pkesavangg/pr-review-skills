package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens for IAM package that can be customized based on app theme
 * This allows the IAM package to adapt to different app themes while maintaining consistency
 */
data class ColorTokens(
  // Background
  val primaryBackground: Color,
  val primaryBackgroundDisabled: Color,
  val secondaryBackground: Color,

  // Status
  val goal: Color,
  val success: Color,
  val secondarySuccess: Color,
  val tertiarySuccess: Color,
  val danger: Color,
  val streak: Color,
  val utility: Color,
  val glow: Color,

  // Text
  val textHeading: Color,
  val textBody: Color,
  val textSubheading: Color,
  val textError: Color,
  val textErrorDisabled: Color,

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

  // Brand
  val meAppPrimary: Color,
  val brandWgPrimary: Color,

  //iam
  val marketingPrimary: Color,
  val marketingPrimaryAction : Color,
  val marketingSecondary : Color,
  val marketingSecondaryAction: Color,
  val marketingTertiary : Color,
  val marketingTertiaryAction : Color,

)
