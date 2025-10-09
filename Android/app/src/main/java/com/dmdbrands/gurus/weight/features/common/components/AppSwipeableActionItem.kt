package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A single action item for use in swipeable list actions, with icon, optional text, background, and click handler.
 *
 * @param iconId The icon resource ID to display.
 * @param text Optional text to display next to the icon.
 * @param contentDescription Content description for accessibility.
 * @param backgroundColor The background color for the action.
 * @param onClick Callback when the action is clicked.
 * @param modifier Modifier for styling.
 */
@Composable
fun AppSwipeableActionItem(
    iconId: Int? = null,
    itemWidth: Dp = 56.dp,
    modifier: Modifier = Modifier,
    text: String? = null,
    contentDescription: String,
    backgroundColor: Color,
    content: @Composable (() -> Unit)? = null,
    shape: Shape = RectangleShape,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .width(itemWidth)
                .fillMaxHeight()
                .background(backgroundColor)
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (iconId != null)
            AppIcon(
                id = iconId,
                contentDescription = contentDescription, // handled by semantics
                modifier = Modifier.size(20.dp),
                type = AppIconType.Secondary,
                onClick = onClick
            )
        if (!text.isNullOrBlank()) {
            Text(
                text = text.uppercase(),
                style = MeTheme.typography.button1,
                color = MeTheme.colorScheme.inverseAction,
            )

            if (content != null) {
                content()
            }
        }
    }
}

// region: Previews

@PreviewTheme
@Composable
private fun PreviewAppSwipeableActionItemLight() {
    MeAppTheme {
        AppScaffold("") {
            AppSwipeableActionItem(
                iconId = AppIcons.Default.Delete,
                text = "Delete",
                contentDescription = "Delete item",
                backgroundColor = MeTheme.colorScheme.danger,
                onClick = {
                },
            )
        }
    }
}

// endregion
