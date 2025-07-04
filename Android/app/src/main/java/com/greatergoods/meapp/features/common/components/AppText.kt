package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.token.LocalSpacing

data class TextAppearance(
    val style: TextStyle,
    val color: Color,
)

enum class TextType {
    Title,
    Subtitle,
    Body,
    Link,
    SubHeading,
    ListTitle1,
    ListTitle2,
    ListSubtitle,
}

object TextTypeDefaults {
    @Composable
    fun appearance(type: TextType): TextAppearance {
        val typography = MeTheme.typography

        return when (type) {
            TextType.Title ->
                TextAppearance(
                    style = typography.heading4,
                    color = colorScheme.textHeading,
                )

            TextType.Subtitle ->
                TextAppearance(
                    style = typography.subHeading1,
                    color = colorScheme.textBody,
                )

            TextType.Body ->
                TextAppearance(
                    style = typography.body2,
                    color = colorScheme.textBody,
                )

            TextType.Link ->
                TextAppearance(
                    style = typography.link1,
                    color = colorScheme.primaryAction,
                )

            TextType.SubHeading ->
                TextAppearance(
                    style = typography.body3,
                    color = colorScheme.textSubheading,
                )

            TextType.ListTitle1 -> TextAppearance(
                style = typography.heading5,
                color = colorScheme.textHeading,
            )

            TextType.ListTitle2 -> TextAppearance(
                style = typography.heading4,
                color = colorScheme.textHeading,
            )

            TextType.ListSubtitle -> TextAppearance(
                style = typography.subHeading2,
                color = colorScheme.textSubheading,
            )

        }
    }
}

@Composable
fun AppText(
    text: String,
    textType: TextType,
    modifier: Modifier = Modifier,
    spacing: Dp = LocalSpacing.current.none,
    textAlign: TextAlign = TextAlign.Start,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val textTypeDefault = TextTypeDefaults.appearance(textType)
    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .let {
                    if (onClick != null) {
                        it.clickable(onClick = onClick)
                    } else {
                        it
                    }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = textTypeDefault.style,
            color = color ?: textTypeDefault.color,
            textAlign = textAlign,
            modifier = modifier,
        )
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
