package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.R.attr.subtitle

enum class CardAlignmentType {
    TopStart,
    TopCenter,
    Center,
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
        modifier =
            modifier
                .background(backgroundColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = MeTheme.spacing.sm)
                    .background(backgroundColor),
            horizontalAlignment =
                when (cardAlignmentType) {
                    CardAlignmentType.TopStart -> Alignment.Start
                    CardAlignmentType.TopCenter -> Alignment.CenterHorizontally
                    CardAlignmentType.Center -> Alignment.CenterHorizontally
                },
            verticalArrangement =
                when (cardAlignmentType) {
                    CardAlignmentType.TopStart -> Arrangement.Top
                    CardAlignmentType.TopCenter -> Arrangement.Top
                    CardAlignmentType.Center -> Arrangement.Center
                },
        ) {
            container()
        }
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
            cardAlignmentType = CardAlignmentType.TopCenter,
        ) {
            AppText("Title-one", TextType.Title)
            AppText("Subtitle", TextType.Subtitle)
            AppText(
                "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.",
                TextType.Body,
            )
        }

        AppStyledCard(
            cardAlignmentType = CardAlignmentType.TopStart,
        ) {
            AppText("Title-two", TextType.Title)
            AppText("Subtitle", TextType.Subtitle)
            AppText(
                "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.",
                TextType.Body,
            )
        }

        AppStyledCard(
            cardAlignmentType = CardAlignmentType.Center,
        ) {
            AppText("Title-three", TextType.Title)
            AppText("Subtitle", TextType.Subtitle)
            AppText(
                "If you have trouble setting up your scale, connect with our team by tapping the help button in the top right.",
                TextType.Body,
            )
        }
    }
}
