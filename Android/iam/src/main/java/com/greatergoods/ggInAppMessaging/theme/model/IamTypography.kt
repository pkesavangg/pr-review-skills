package com.greatergoods.ggInAppMessaging.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextStyle

@Stable
data class IamTypography(
  val heading1: TextStyle, // Largest heading
  val heading2: TextStyle, // Second largest heading
  val heading3: TextStyle, // Third largest heading
  val heading4: TextStyle, // Fourth heading
  val heading5: TextStyle, // Fifth heading
  val heading6: TextStyle, // Sixth heading
  val subHeading1: TextStyle, // Primary subheading
  val subHeading2: TextStyle, // Secondary subheading
  val body1: TextStyle, // Main body text
  val body2: TextStyle, // Secondary body text
  val body3: TextStyle, // Tertiary body text
  val body4: TextStyle, // Fourth body text
  val body5: TextStyle, // Fifth body text
  val link1: TextStyle, // Primary link
  val link2: TextStyle, // Secondary link
  val button1: TextStyle, // Primary button
  val button2: TextStyle, // Secondary button
)
