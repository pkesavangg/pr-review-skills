package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.greatergoods.ggInAppMessaging.theme.rememberIAMColorScheme
import com.greatergoods.ggInAppMessaging.theme.getIAMColors

/**
 * IAM Theme package index
 *
 * This package provides a comprehensive color system for the IAM package that:
 * 1. Follows the same semantic structure as the main app
 * 2. Automatically adapts to light and dark modes
 * 3. Provides IAM-specific colors and themes
 * 4. Integrates seamlessly with existing MaterialTheme usage
 *
 * Usage:
 *
 * // Get IAM colors from MaterialTheme
 * val iamColors = getIAMColors()
 *
 * // Use semantic colors
 * val backgroundColor = iamColors.backgroundPrimary
 * val textColor = iamColors.textHeading
 * val actionColor = iamColors.actionPrimary
 *
 * // Get theme-specific colors
 * val themeColor = iamColors.getThemeColor("red")
 * val promoColors = iamColors.getPromoCodeColors("blue")
 *
 * // Manual color scheme creation
 * val iamColorScheme = IAMColorScheme(isDarkMode = true)
 * val darkModeColors = iamColorScheme.actionPrimary
 */

/**
 * Quick access to common IAM colors
 */
object IAMThemeColors {
  /**
   * Get IAM color scheme for current theme
   */
  @Composable
  fun current(): IAMColorScheme {
    return getIAMColors()
  }

  /**
   * Get IAM color scheme for specific theme mode
   */
  @Composable
  fun forMode(isDarkMode: Boolean): IAMColorScheme {
    return rememberIAMColorScheme(isDarkMode)
  }
}
