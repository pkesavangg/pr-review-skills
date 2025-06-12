/**
 * Defines the application's typography system for Jetpack Compose, based on the design system.
 *
 * Each property represents a semantic typography role (heading, subheading, body, link, button, etc.)
 * and is mapped to a TextStyle for consistent text appearance across the app.
 */
package com.greatergoods.meapp.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.R
import com.greatergoods.meapp.theme.model.Typography

// Define OpenSans font family using all relevant weights
val OpenSansFontFamily =
    FontFamily(
        Font(R.font.open_sans_light, FontWeight.W300),
        Font(R.font.open_sans_regular, FontWeight.W400),
        Font(R.font.open_sans_medium, FontWeight.W500),
        Font(R.font.open_sans_semi_bold, FontWeight.W600),
        Font(R.font.open_sans_bold, FontWeight.W700),
        Font(R.font.open_sans_extra_bold, FontWeight.W800),
    )

/**
 * App-wide typography definitions, mapping semantic roles to TextStyle values.
 */
val AppTypography =
    Typography(
        // Heading
        heading1 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 60.sp,
                lineHeight = 68.sp,
                letterSpacing = 0.sp,
            ),
        heading2 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 50.sp,
                lineHeight = 58.sp,
                letterSpacing = 0.sp,
            ),
        heading3 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        heading4 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        heading5 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        // Subheading
        subHeading1 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        subHeading2 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        // Body
        body1 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        body2 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        body3 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        // Link
        link1 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        link2 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
        // Button
        button1 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        button2 =
            TextStyle(
                fontFamily = OpenSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
    )

/**
 * CompositionLocal for accessing the current [Typography] instance in the Compose hierarchy.
 */
val LocalTypography = staticCompositionLocalOf<Typography> { error("No Typography provided") }
