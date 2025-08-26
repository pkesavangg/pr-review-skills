package com.greatergoods.ggInAppMessaging.domain.constants

import androidx.compose.ui.graphics.Color
import com.greatergoods.ggInAppMessaging.theme.IAMColorScheme

/**
 * Promotion themes with color schemes
 * Android equivalent of Angular promotionThemes.ts
 * Now integrated with IAM color system
 */
object PromotionThemes {

    /**
     * Get theme colors by theme name using IAM color scheme
     */
    fun getThemeColors(themeName: String, iamColorScheme: IAMColorScheme): ThemeColors {
        val baseColor = iamColorScheme.getThemeColor(themeName)
        val promoCodeBgColor = baseColor.copy(alpha = 0.2f)

        return ThemeColors(
            promoCodeColor = baseColor,
            promoCodeBgColor = promoCodeBgColor,
            copyButtonBgColor = baseColor,
            promoCodeBgColorDarkMode = promoCodeBgColor,
            copyButtonBgColorDarkMode = baseColor
        )
    }

    /**
     * Legacy method for backward compatibility
     */
    fun getThemeColors(themeName: String): ThemeColors {
        return when (themeName.lowercase()) {
            "red" -> ThemeColors(
                promoCodeColor = Color(0xFFD9675C),
                promoCodeBgColor = Color(0x33B8584E),
                copyButtonBgColor = Color(0xFFB8584E),
                promoCodeBgColorDarkMode = Color(0x33D9675C),
                copyButtonBgColorDarkMode = Color(0xFFD9675C)
            )
            "green" -> ThemeColors(
                promoCodeColor = Color(0xFF9DAD99),
                promoCodeBgColor = Color(0x336E796B),
                copyButtonBgColor = Color(0xFF6E796B),
                promoCodeBgColorDarkMode = Color(0x339DAD99),
                copyButtonBgColorDarkMode = Color(0xFF9DAD99)
            )
            "blue" -> ThemeColors(
                promoCodeColor = Color(0xFF4E738A),
                promoCodeBgColor = Color(0x334E738A),
                copyButtonBgColor = Color(0xFF4E738A),
                promoCodeBgColorDarkMode = Color(0x33839DAD),
                copyButtonBgColorDarkMode = Color(0xFF839DAD)
            )
            else -> ThemeColors(
                promoCodeColor = Color(0xFF424242),
                promoCodeBgColor = Color(0xFFFCF8F4),
                copyButtonBgColor = Color(0xFF424242),
                promoCodeBgColorDarkMode = Color(0x33FCF8F4),
                copyButtonBgColorDarkMode = Color(0xFFFCF8F4)
            )
        }
    }
}

/**
 * Theme colors data class
 */
data class ThemeColors(
    val promoCodeColor: Color,
    val promoCodeBgColor: Color,
    val copyButtonBgColor: Color,
    val promoCodeBgColorDarkMode: Color,
    val copyButtonBgColorDarkMode: Color
)
