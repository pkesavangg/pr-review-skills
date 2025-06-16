package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme
import com.greatergoods.meapp.theme.model.Spacing
import com.greatergoods.meapp.theme.token.LocalSpacing

data class TextAppearance(
    val style: TextStyle,
    val color: Color,
)

enum class TextType {
    Title,
    Subtitle,
    Body,
    link,
}

object TextTypeDefaults {
    @Composable
    fun appearance(type: TextType): TextAppearance {
        val typography = MeAppTheme.typography

        return when (type) {
            TextType.Title ->
                TextAppearance(
                    style = typography.heading4,
                    color = colorScheme.heading,
                )

            TextType.Subtitle ->
                TextAppearance(
                    style = typography.subHeading2,
                    color = colorScheme.body,
                )

            TextType.Body ->
                TextAppearance(
                    style = typography.body2,
                    color = colorScheme.body,
                )

            TextType.link ->
                TextAppearance(
                    style = typography.link2,
                    color = colorScheme.primaryAction,
                )
        }
    }
}

@Composable
fun AppText(
    text: String,
    textType: TextType,
    action: (() -> Unit)? = null,
    spacing: Dp = LocalSpacing.current.none
    ) {
    val textTypeDefault = TextTypeDefaults.appearance(textType)
    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .let {
                    if (action != null) it.clickable(onClick = action)
                    else it
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text(text, style = textTypeDefault.style, color = textTypeDefault.color)
        Spacer(Modifier.height(spacing))
    }
}

@PreviewTheme
@Composable
fun AppTextPreview() {
    MeAppTheme {
        Column {
            AppText("Title", TextType.Title)
            AppText("Sub title", TextType.Subtitle)
            AppText("Body", TextType.Body)
        }
    }
}
