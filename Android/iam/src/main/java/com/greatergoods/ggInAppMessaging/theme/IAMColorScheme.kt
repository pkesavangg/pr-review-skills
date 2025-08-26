package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.greatergoods.ggInAppMessaging.theme.token.IAMDarkColorToken
import com.greatergoods.ggInAppMessaging.theme.token.IAMLightColorToken

/**
 * IAM Color Scheme that provides semantic color tokens based on the current theme.
 *
 * This class integrates with the main app's theme system and provides IAM-specific colors
 * that automatically adapt to light and dark modes.
 */
class IAMColorScheme(
    val isDarkMode: Boolean
) {
    // Background colors
    val backgroundPrimary: Color get() = if (isDarkMode) IAMDarkColorToken.primary else IAMLightColorToken.primary
    val backgroundSecondary: Color get() = if (isDarkMode) IAMDarkColorToken.secondary else IAMLightColorToken.secondary
    val backgroundTertiary: Color get() = if (isDarkMode) IAMDarkColorToken.tertiary else IAMLightColorToken.tertiary
    val backgroundCard: Color get() = if (isDarkMode) IAMDarkColorToken.cardBackground else IAMLightColorToken.cardBackground

    // Status colors
    val statusGoal: Color get() = if (isDarkMode) IAMDarkColorToken.goal else IAMLightColorToken.goal
    val statusSuccess: Color get() = if (isDarkMode) IAMDarkColorToken.success else IAMLightColorToken.success
    val statusDanger: Color get() = if (isDarkMode) IAMDarkColorToken.danger else IAMLightColorToken.danger
    val statusWarning: Color get() = if (isDarkMode) IAMDarkColorToken.warning else IAMLightColorToken.warning
    val statusUtility: Color get() = if (isDarkMode) IAMDarkColorToken.utility else IAMLightColorToken.utility
    val statusGlow: Color get() = if (isDarkMode) IAMDarkColorToken.glow else IAMLightColorToken.glow

    // Text colors
    val textHeading: Color get() = if (isDarkMode) IAMDarkColorToken.heading else IAMLightColorToken.heading
    val textBody: Color get() = if (isDarkMode) IAMDarkColorToken.body else IAMLightColorToken.body
    val textSubheading: Color get() = if (isDarkMode) IAMDarkColorToken.subheading else IAMLightColorToken.subheading
    val textError: Color get() = if (isDarkMode) IAMDarkColorToken.textError else IAMLightColorToken.textError
    val textErrorDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.textErrorDisabled else IAMLightColorToken.textErrorDisabled
    val textSuccess: Color get() = if (isDarkMode) IAMDarkColorToken.textSuccess else IAMLightColorToken.textSuccess
    val textWarning: Color get() = if (isDarkMode) IAMDarkColorToken.textWarning else IAMLightColorToken.textWarning

    // Action colors
    val actionPrimary: Color get() = if (isDarkMode) IAMDarkColorToken.primaryAction else IAMLightColorToken.primaryAction
    val actionPrimaryDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.primaryActionDisabled else IAMLightColorToken.primaryActionDisabled
    val actionSecondary: Color get() = if (isDarkMode) IAMDarkColorToken.secondaryAction else IAMLightColorToken.secondaryAction
    val actionSecondaryDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.secondaryActionDisabled else IAMLightColorToken.secondaryActionDisabled
    val actionTertiary: Color get() = if (isDarkMode) IAMDarkColorToken.tertiaryAction else IAMLightColorToken.tertiaryAction
    val actionTertiaryDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.tertiaryActionDisabled else IAMLightColorToken.tertiaryActionDisabled
    val actionInverse: Color get() = if (isDarkMode) IAMDarkColorToken.inverse else IAMLightColorToken.inverse
    val actionInverseDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.inverseDisabled else IAMLightColorToken.inverseDisabled
    val actionInverseSecondary: Color get() = if (isDarkMode) IAMDarkColorToken.inverseSecondary else IAMLightColorToken.inverseSecondary
    val actionError: Color get() = if (isDarkMode) IAMDarkColorToken.errorAction else IAMLightColorToken.errorAction
    val actionErrorDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.errorActionDisabled else IAMLightColorToken.errorActionDisabled

    // Icon colors
    val iconPrimary: Color get() = if (isDarkMode) IAMDarkColorToken.iconPrimary else IAMLightColorToken.iconPrimary
    val iconPrimaryDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.iconPrimaryDisabled else IAMLightColorToken.iconPrimaryDisabled
    val iconSecondary: Color get() = if (isDarkMode) IAMDarkColorToken.iconSecondary else IAMLightColorToken.iconSecondary
    val iconSecondaryDisabled: Color get() = if (isDarkMode) IAMDarkColorToken.iconSecondaryDisabled else IAMLightColorToken.iconSecondaryDisabled
    val iconSuccess: Color get() = if (isDarkMode) IAMDarkColorToken.iconSuccess else IAMLightColorToken.iconSuccess
    val iconWarning: Color get() = if (isDarkMode) IAMDarkColorToken.iconWarning else IAMLightColorToken.iconWarning
    val iconError: Color get() = if (isDarkMode) IAMDarkColorToken.iconError else IAMLightColorToken.iconError

    // Loading colors
    val loading: Color get() = if (isDarkMode) IAMDarkColorToken.loading else IAMLightColorToken.loading
    val loadingError: Color get() = if (isDarkMode) IAMDarkColorToken.loadingError else IAMLightColorToken.loadingError
    val loadingSuccess: Color get() = if (isDarkMode) IAMDarkColorToken.loadingSuccess else IAMLightColorToken.loadingSuccess

    // Support colors
    val overlay: Color get() = if (isDarkMode) IAMDarkColorToken.overlay else IAMLightColorToken.overlay
    val toastBackground: Color get() = if (isDarkMode) IAMDarkColorToken.toastBackground else IAMLightColorToken.toastBackground
    val divider: Color get() = if (isDarkMode) IAMDarkColorToken.divider else IAMLightColorToken.divider

    // Brand colors
    val brandMeAppPrimary: Color get() = if (isDarkMode) IAMDarkColorToken.meAppPrimary else IAMLightColorToken.meAppPrimary
    val brandWgPrimary: Color get() = if (isDarkMode) IAMDarkColorToken.wgPrimary else IAMLightColorToken.wgPrimary

    // IAM-specific colors
    val promoCodeBackground: Color get() = if (isDarkMode) IAMDarkColorToken.promoCodeBackground else IAMLightColorToken.promoCodeBackground
    val promoCodeText: Color get() = if (isDarkMode) IAMDarkColorToken.promoCodeText else IAMLightColorToken.promoCodeText
    val copyButtonBackground: Color get() = if (isDarkMode) IAMDarkColorToken.copyButtonBackground else IAMLightColorToken.copyButtonBackground
    val copyButtonText: Color get() = if (isDarkMode) IAMDarkColorToken.copyButtonText else IAMLightColorToken.copyButtonText

    // Theme-specific colors
    val themeRed: Color get() = if (isDarkMode) IAMDarkColorToken.themeRed else IAMLightColorToken.themeRed
    val themeGreen: Color get() = if (isDarkMode) IAMDarkColorToken.themeGreen else IAMLightColorToken.themeGreen
    val themeBlue: Color get() = if (isDarkMode) IAMDarkColorToken.themeBlue else IAMLightColorToken.themeBlue
    val themeGray: Color get() = if (isDarkMode) IAMDarkColorToken.themeGray else IAMLightColorToken.themeGray

    /**
     * Get theme color by name
     */
    fun getThemeColor(themeName: String?): Color {
        return when (themeName?.lowercase()) {
            "red" -> themeRed
            "green" -> themeGreen
            "blue" -> themeBlue
            else -> themeGray
        }
    }

    /**
     * Get promo code colors for a specific theme
     */
    fun getPromoCodeColors(themeName: String?): PromoCodeColors {
        val baseColor = getThemeColor(themeName)
        return PromoCodeColors(
            background = baseColor.copy(alpha = 0.2f),
            text = baseColor,
            copyButton = baseColor
        )
    }
}

/**
 * Promo code color scheme
 */
data class PromoCodeColors(
    val background: Color,
    val text: Color,
    val copyButton: Color
)

/**
 * Composable function to get IAM color scheme
 */
@Composable
fun rememberIAMColorScheme(isDarkMode: Boolean): IAMColorScheme {
    return remember(isDarkMode) {
        IAMColorScheme(isDarkMode)
    }
}

/**
 * Helper function to get IAM colors from MaterialTheme
 * This allows easy integration with existing MaterialTheme usage
 */
@Composable
fun getIAMColors(): IAMColorScheme {
    val isDarkMode = MaterialTheme.colorScheme.surface == Color(0xFF222D39) // Check if dark theme
    return rememberIAMColorScheme(isDarkMode)
}
