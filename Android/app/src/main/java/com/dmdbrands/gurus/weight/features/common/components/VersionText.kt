package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.dmdbrands.gurus.weight.features.common.utils.rememberVersionText
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

/**
 * Reusable composable that displays version information based on build type.
 * Shows version number for production builds and build number for debug/release builds.
 *
 * @param modifier Modifier for styling the text
 * @param textAlign Text alignment, defaults to Center
 * @param color Text color, defaults to theme textSubheading
 * @param style Text style, defaults to theme body4
 */
@Composable
fun VersionText(
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = colorScheme.textSubheading,
    style: TextStyle = typography.body4,
    titlePrefix: String? = null
) {
  val versionText = rememberVersionText()
  val title = if (!titlePrefix.isNullOrEmpty()) "$titlePrefix $versionText" else versionText

  Text(
    text = title,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
}
