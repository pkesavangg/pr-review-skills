package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Utility for parsing theme colors from server data
 */
object ThemeColorParser {

  /**
   * Parses theme color from server and returns marketing colors using IAM theme
   * @param theme The theme color string from server (e.g., "red", "blue", etc.)
   * @return MarketingColors with parsed colors or default values
   */
  @Composable
  fun parseMarketingColors(theme: String?): MarketingColors {
    val iamColors = IamTheme.colors

    return when (theme?.lowercase()) {
      "red" -> MarketingColors(
        primary = iamColors.marketingPrimary, // Use IAM theme marketing primary
        primaryAction = iamColors.marketingPrimaryAction // Use IAM theme marketing primary action
      )
      "blue" -> MarketingColors(
        primary = iamColors.marketingSecondary, // Light blue background
        primaryAction = iamColors.marketingSecondaryAction // Blue action color
      )
      "green" -> MarketingColors(
        primary = iamColors.marketingTertiary, // Light green background
        primaryAction = iamColors.marketingTertiaryAction // Green action color
      )
      else -> MarketingColors(
        primary = iamColors.tertiaryBackground, // Use IAM theme default
        primaryAction = iamColors.subSecondaryBackground // Use IAM theme default
      )
    }
  }

  /**
   * Data class for marketing colors
   */
  data class MarketingColors(
    val primary: Color,
    val primaryAction: Color
  )
}
