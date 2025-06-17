package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import android.R.attr.textAlignment

data class TextAppearance(
    val style: TextStyle,
    val color: Color,
    val padding: PaddingValues,
)

enum class TextType {
    Title,
    Subtitle,
    Body,
    SubHeading,
}

object TextTypeDefaults {
    @Composable
    fun appearance(type: TextType): TextAppearance {
        val typography = MeAppTheme.typography

        return when (type) {
            TextType.Title ->
                TextAppearance(
                    style = typography.heading4,
                    color = MeAppTheme.colorScheme.heading,
                    padding = PaddingValues(bottom = MeAppTheme.spacing.xs),
                )

            TextType.Subtitle ->
                TextAppearance(
                    style = typography.subHeading2,
                    color = MeAppTheme.colorScheme.body,
                    padding = PaddingValues(bottom = MeAppTheme.spacing.lg),
                )

            TextType.Body ->
                TextAppearance(
                    style = typography.body2,
                    color = MeAppTheme.colorScheme.body,
                    padding = PaddingValues(bottom = 0.dp),
                )
            TextType.SubHeading ->
                TextAppearance(
                    style = typography.body3,
                    color = MeAppTheme.colorScheme.subheading,
                    padding = PaddingValues(bottom = 0.dp),
                )
        }
    }
}

@Composable
fun AppText(
    text: String,
    textType: TextType,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val textTypeDefault = TextTypeDefaults.appearance(textType)
    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .padding(textTypeDefault.padding),
    ) {
        Text(text, style = textTypeDefault.style, color = textTypeDefault.color, textAlign = textAlign, modifier = modifier)
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
