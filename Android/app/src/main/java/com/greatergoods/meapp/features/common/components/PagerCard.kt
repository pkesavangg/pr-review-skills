package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import android.R.attr.subtitle

data class TextAppearance(
    val style: TextStyle,
    val color: Color,
    val padding: PaddingValues
)

enum class TextType {
    Title,
    Subtitle,
    Body
}

enum class CardAlignmentType {
    TopStart,
    TopCenter,
    Center
}


object TextTypeDefaults {

    @Composable
    fun appearance(type: TextType): TextAppearance {
        val typography = MeAppTheme.typography

        return when (type) {
            TextType.Title -> TextAppearance(
                style = typography.heading4,
                color = MeAppTheme.colorScheme.heading,
                padding = PaddingValues( bottom = MeAppTheme.spacing.xs)
            )
            TextType.Subtitle -> TextAppearance(
                style = typography.subHeading2,
                color = MeAppTheme.colorScheme.body,
                padding = PaddingValues(bottom = MeAppTheme.spacing.lg)
            )
            TextType.Body -> TextAppearance(
                style = typography.body2,
                color = MeAppTheme.colorScheme.body,
                padding =  PaddingValues(bottom = 0.dp)
            )
        }
    }
}


/**
 * A customizable pager card component for displaying structured content with title, subtitle, and body.
 *
 * This component provides a flexible card layout that can be used in pager components
 * or standalone cards with consistent styling and design system integration.
 *
 * This implementation uses Box with background instead of Surface
 * for more direct control over styling without additional component layers.
 *
 * @param subtitle Optional subtitle/description text below the title
 * @param modifier Modifier to be applied to the card */

@Composable
fun AppStyledCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    cardAlignmentType: CardAlignmentType = CardAlignmentType.TopStart,
    container: @Composable () -> Unit,
    ) {
        Box(
            modifier = modifier
                .background(backgroundColor),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = MeAppTheme.spacing.sm)
                    .background(backgroundColor)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = when(cardAlignmentType){
                    CardAlignmentType.TopStart -> Alignment.Start
                    CardAlignmentType.TopCenter -> Alignment.CenterHorizontally
                    CardAlignmentType.Center -> Alignment.CenterHorizontally
                },
                verticalArrangement = when(cardAlignmentType){
                    CardAlignmentType.TopStart -> Arrangement.Top
                    CardAlignmentType.TopCenter -> Arrangement.Top
                    CardAlignmentType.Center -> Arrangement.Center
                }
            ) {
                container()
            }
        }
}

@Composable
fun AppText(
    textType: TextType,
    text: String,
){
    val textTypeDefault = TextTypeDefaults.appearance(textType)
    Column(
        modifier = Modifier
            .wrapContentSize()
            .padding(textTypeDefault.padding)
    ) {
        Text(text, style = textTypeDefault.style)
    }
}


/**
 * Preview of PagerCard component with just title and subtitle
 */
@PreviewTheme
@Composable
fun PagerCardSimplePreview() {
    MeAppTheme {
            AppStyledCard(
                cardAlignmentType = CardAlignmentType.TopCenter
            )
            {
                AppText(TextType.Title, "Title-one")
                AppText(TextType.Subtitle, "Subtitle")
                AppText(TextType.Body, "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.")
            }

        AppStyledCard(
            cardAlignmentType = CardAlignmentType.TopStart
        )
        {
            AppText(TextType.Title, "Title-two")
            AppText(TextType.Subtitle, "Subtitle")
            AppText(TextType.Body, "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.")
        }

        AppStyledCard(
            cardAlignmentType = CardAlignmentType.Center
        )
        {
            AppText(TextType.Title, "Title-three")
            AppText(TextType.Subtitle, "Subtitle")
            AppText(TextType.Body, "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.")
        }
        }
}
