package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * A single action item for use in draggable list actions, with icon, optional text, background, and click handler.
 *
 * @param iconId The icon resource ID to display.
 * @param text Optional text to display next to the icon.
 * @param contentDescription Content description for accessibility.
 * @param backgroundColor The background color for the action.
 * @param onClick Callback when the action is clicked.
 * @param modifier Modifier for styling.
 */
@Composable
fun AppDraggableActionItem(
    iconId: Int,
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
                .width(56.dp)
                .fillMaxHeight()
                .background(backgroundColor)
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { this.contentDescription = contentDescription }
                ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AppIcon(
            id = iconId,
            contentDescription = contentDescription, // handled by semantics
            modifier = Modifier.size(20.dp),
            type = AppIconType.Secondary,
        )
        if (!text.isNullOrBlank()) {
            if (content != null) {
                content()
            }
        }
    }
}

// region: Previews

@PreviewTheme
@Composable
private fun PreviewAppDraggableActionItemLight() {
    MeAppTheme {
        AppScaffold("") {
            AppDraggableActionItem(
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
