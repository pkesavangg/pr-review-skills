package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * A circular profile image that displays the first letter of the provided text.
 *
 * @param text The text from which to extract the first letter
 * @param modifier Modifier for styling
 * @param size Size of the circular profile image in dp
 * @param backgroundColor Background color of the circle
 * @param textColor Color of the letter
 */
@Composable
fun AppProfileAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    backgroundColor: Color = MeTheme.colorScheme.primaryAction,
    textColor: Color = MeTheme.colorScheme.inverseAction,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.firstOrNull()?.uppercase() ?: "",
            style = MeTheme.typography.heading6,
            color = textColor,
        )
    }
}

@PreviewTheme
@Composable
fun AppProfileImagePreview() {
    MeAppTheme {
        AppScaffold("") {
            AppProfileAvatar(text = "Kevin")
        }
    }
}
