package com.dmdbrands.gurus.weight.theme.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextStyle

/**
 * Defines the semantic typography roles for the application's theme, as per the design system reference.
 *
 * Each property represents a semantic text style (heading, subheading, body, link, button, etc.)
 * and is mapped to a [TextStyle] for consistent text appearance across the app.
 *
 * @property heading1 Largest heading style, used for major titles.
 * @property heading2 Second largest heading style, used for section headers.
 * @property heading3 Third largest heading style, used for sub-section headers.
 * @property heading4 Fourth heading style, used for smaller headers.
 * @property heading5 Fifth heading style, used for minor headers.
 * @property heading6 Sixth heading style, used for minor headers.
 * @property subHeading1 Primary subheading style, used for supporting text under headings.
 * @property subHeading2 Secondary subheading style, used for less prominent supporting text.
 * @property body1 Main body text style, used for primary content.
 * @property body2 Secondary body text style, used for secondary content.
 * @property body3 Tertiary body text style, used for less prominent content.
 * @property body4 Fourth body text style, used for less prominent content.
 * @property body5 Fifth body text style, used for less prominent content.
 * @property link1 Primary link style, used for main interactive text links.
 * @property link2 Secondary link style, used for less prominent links.
 * @property button1 Primary button text style.
 * @property button2 Secondary button text style.
 */
@Stable
data class Typography(
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
