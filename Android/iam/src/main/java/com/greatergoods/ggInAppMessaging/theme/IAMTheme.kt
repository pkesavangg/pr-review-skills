package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.greatergoods.ggInAppMessaging.theme.model.IamTypography

@Immutable
data class IamColors(
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
  val danger: Color,
  val streak: Color,
  val utility: Color,
  val glow: Color,

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

  // Brand
  val meAppPrimary: Color,
  val wgPrimary: Color,

  //iam
  val marketingPrimary: Color,
  val marketingPrimaryAction : Color,
  val marketingSecondary : Color,
  val marketingSecondaryAction: Color,
  val marketingTertiary : Color,
  val marketingTertiaryAction : Color,
)

// Default colors for IAM previews when app doesn't provide values
private val DefaultIamColors = IamColors(
  // Background
  primaryBackground = Color.White,
  primaryBackgroundDisabled = Color(0xFFF5F5F5),
  secondaryBackground = Color(0xFFF6F4F1),
  subSecondaryBackground = Color(0xFF424242),
  tertiaryBackground = Color(0x33424242),

  // Action
  primaryFocusedAction = Color(0xFF1565C0),
  primaryAction = Color(0xFF1565C0),
  primaryActionDisabled = Color(0xFFB8D6F4),
  secondaryAction = Color(0xFF2C2827),
  secondaryActionDisabled = Color(0xFFD0CCCA),
  tertiaryAction = Color(0xFF7B726E),
  tertiaryActionDisabled = Color(0xFFD0CCCA),
  tertiaryActionSecondary = Color(0xFF7B726E),
  inverseAction = Color.White,
  inverseActionDisabled = Color(0xFFF5F5F5),
  inverseActionSecondary = Color(0xFFF6F4F1),
  errorAction = Color(0xFFB3261E),
  errorActionDisabled = Color(0xFFF5C0BD),
  errorActionSecondary = Color(0xFFB3261E),

  // Status
  goal = Color(0xFF458239),
  success = Color(0xFF458239),
  danger = Color(0xFFB3261E),
  streak = Color(0xFFEDB53A),
  utility = Color(0xFFD0CCCA),
  glow = Color(0x40000000),

  // Icon
  iconPrimary = Color(0xFF1565C0),
  iconPrimaryDisabled = Color(0xFFB8D6F4),
  iconSecondary = Color(0xFF7B726E),
  iconSecondaryDisabled = Color(0xFFD0CCCA),

  // Loading
  loading = Color(0xFFB8D6F4),
  loadingError = Color(0xFFF5C0BD),

  // Support
  overlay = Color(0x802C2827),
  toastBackground = Color(0xFFE3F2FD),

  // Text
  textHeading = Color(0xFF2C2827),
  textBody = Color(0xFF2C2827),
  textSubheading = Color(0xFF7B726E),
  textError = Color(0xFFB3261E),
  textErrorDisabled = Color(0xFFF5C0BD),

  // Brand
  meAppPrimary = Color(0xFF65CEC8),
  wgPrimary = Color(0xFF1565C0),

  //iam
  marketingPrimary = Color(0xFF1565C0),
  marketingPrimaryAction = Color(0xFF1565C0),
  marketingSecondary = Color(0xFF2C2827),
  marketingSecondaryAction = Color(0xFF2C2827),
  marketingTertiary = Color(0xFF7B726E),
  marketingTertiaryAction = Color(0xFF7B726E)
)

// LocalComposition for IAM colors - app provides colors through this
val LocalIamColors = staticCompositionLocalOf { DefaultIamColors }
val LocalIamTypography = staticCompositionLocalOf<IamTypography> { error("No Typography provided") }

object IamTheme {
  val colors: IamColors
    @Composable get() = LocalIamColors.current

  // Typography
  val typography: IamTypography
    @Composable @ReadOnlyComposable
    get() = LocalIamTypography.current
}

/**
 * Provides IAM theme colors to child composables.
 * This is an alternative to using LocalIamColors directly.
 */
@Composable
fun ProvideIamTheme(
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(LocalIamColors provides IamTheme.colors, LocalIamTypography provides IamTypography) {
    content()
  }
}
