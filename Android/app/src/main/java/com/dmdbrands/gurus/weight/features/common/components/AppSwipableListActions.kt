package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * Displays trailing actions for a swipeable list item, allowing any number of custom actions.
 *
 * @param actions Composable lambda for the actions to display (e.g., icons, buttons).
 */
@Composable
fun RowScope.AppSwipeableListActions(
    shape: Shape = RectangleShape,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxHeight()
                .clip(shape),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = actions,
    )
}

// region: Previews

@PreviewTheme
@Composable
private fun PreviewAppSwipeableListActionsLight() {
    MeAppTheme {
        AppScaffold("") {
            Row {
                AppSwipeableListActions {
                    AppIcon(
                      id = com.dmdbrands.gurus.weight.resources.AppIcons.Default.Delete,
                      contentDescription = "Delete",
                      modifier = Modifier,
                      type = AppIconType.Inverse,
                    )
                    AppIcon(
                      id = com.dmdbrands.gurus.weight.resources.AppIcons.Default.Graph,
                      contentDescription = "Edit",
                      modifier = Modifier,
                      type = AppIconType.Inverse,
                    )
                }
            }
        }
    }
}

// endregion
