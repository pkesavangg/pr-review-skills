package com.greatergoods.ggInAppMessaging.theme.enums

import androidx.compose.ui.graphics.Color

/**
 * Color palette specifically for the IAM (In-App Messaging) package
 * Extends the main app's color system with IAM-specific colors
 */
object IAMColorPalette {
    // IAM-specific colors
    val IAMRed100 = Color(0xFFF5C0BD)
    val IAMRed200 = Color(0xFFD9675C)
    val IAMRed300 = Color(0xFFB8584E)
    val IAMRed400 = Color(0xFFB3261E)
    val IAMRed500 = Color(0xFFF28B82)
    val IAMRed900 = Color(0xFFB3261E)
    val IAMRed950 = Color(0xFF5C1A16)

    val IAMGreen100 = Color(0xFF63B453)
    val IAMGreen200 = Color(0xFF458239)
    val IAMGreen300 = Color(0xFF9DAD99)
    val IAMGreen400 = Color(0xFF6E796B)

    val IAMBlue100 = Color(0xFFE3F2FD)
    val IAMBlue200 = Color(0xFFB8D6F4)
    val IAMBlue300 = Color(0xFF4E738A)
    val IAMBlue400 = Color(0xFF2B8AEB)
    val IAMBlue500 = Color(0x801565C0)
    val IAMBlue600 = Color(0x802B8AEB)
    val IAMBlue900 = Color(0xFF1565C0)
    val IAMBlue950 = Color(0x662B8AEB)
    val IAMBlue1000 = Color(0xFF1A3959)

    val IAMGray100 = Color(0xFFFFFFFF)
    val IAMGray150 = Color(0xFFF6F4F1)
    val IAMGray200 = Color(0xFFFCF8F4)
    val IAMGray300 = Color(0xFF424242)
    val IAMGray400 = Color(0xFFD0CCCA)
    val IAMGray500 = Color(0x402C2827)
    val IAMGray600 = Color(0xFF7B726E)
    val IAMGray700 = Color(0xFFE0E1E1)
    val IAMGray800 = Color(0xFF565F68)
    val IAMGray850 = Color(0x80222D39) // 50% of #222D39
    val IAMGray900 = Color(0xFF222D39)
    val IAMGray950 = Color(0xFF12161B)
    val IAMGray1000 = Color(0xFF2C2827)

    val IAMTeal100 = Color(0xFF65CEC8)
    val IAMTeal200 = Color(0xFF00B3A6)

    val IAMYellow100 = Color(0xFFFDD663)
    val IAMYellow200 = Color(0xFFEDB53A)

    // IAM-specific accent colors
    val IAMAccentPrimary = Color(0xFF1565C0)
    val IAMAccentSecondary = Color(0xFF2B8AEB)
    val IAMAccentSuccess = Color(0xFF458239)
    val IAMAccentWarning = Color(0xFFEDB53A)
    val IAMAccentError = Color(0xFFB3261E)
    val IAMAccentInfo = Color(0xFF4E738A)

    // IAM-specific overlay colors
    val IAMOverlayLight = Color(0x40000000) // 25% black
    val IAMOverlayDark = Color(0x40FFFFFF)  // 25% white
    val IAMOverlayMedium = Color(0x80000000) // 50% black
    val IAMOverlayMediumLight = Color(0x80FFFFFF) // 50% white

    // IAM-specific background colors
    val IAMBackgroundLight = Color(0xFFF6F4F1)
    val IAMBackgroundDark = Color(0xFF12161B)
    val IAMSurfaceLight = Color(0xFFFFFFFF)
    val IAMSurfaceDark = Color(0xFF222D39)

    // IAM-specific text colors
    val IAMTextPrimaryLight = Color(0xFF2C2827)
    val IAMTextPrimaryDark = Color(0xFFE0E1E1)
    val IAMTextSecondaryLight = Color(0xFF7B726E)
    val IAMTextSecondaryDark = Color(0xFF92989F)
    val IAMTextDisabledLight = Color(0xFFD0CCCA)
    val IAMTextDisabledDark = Color(0xFF565F68)
}
