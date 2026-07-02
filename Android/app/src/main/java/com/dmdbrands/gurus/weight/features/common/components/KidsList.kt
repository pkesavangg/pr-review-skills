package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme

/** A single kid row for [KidsList]: stable [id] plus the display [name]. */
data class KidListItem(
    val id: String,
    val name: String,
)

/**
 * Shared list of kid rows with edit (trailing pencil) and delete (swipe) actions, used by the
 * signup Add-Baby step and the Settings → My Kids screen so both render identically.
 * Reuses the same swipe primitive + [AppProfileAvatar] as the switch-account list (Figma 31880-34959),
 * with thin dividers between rows and outer corners rounded by row position.
 *
 * Callers own the surrounding title, container, and the "Add a baby" button.
 */
@Composable
fun KidsList(
    kids: List<KidListItem>,
    editContentDescription: String,
    deleteContentDescription: String,
    onEditKid: (String) -> Unit,
    onDeleteKid: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        kids.forEachIndexed { index, kid ->
            KidRow(
                kid = kid,
                index = index,
                count = kids.size,
                isOpen = openIndex == index,
                onActionOpened = { openedIdx -> openIndex = openedIdx },
                editContentDescription = editContentDescription,
                deleteContentDescription = deleteContentDescription,
                onEditKid = onEditKid,
                onDeleteKid = onDeleteKid,
            )
        }
    }
}

/** A single swipeable kid row with position-aware rounded corners and a divider. */
@Composable
private fun KidRow(
    kid: KidListItem,
    index: Int,
    count: Int,
    isOpen: Boolean,
    onActionOpened: (Int?) -> Unit,
    editContentDescription: String,
    deleteContentDescription: String,
    onEditKid: (String) -> Unit,
    onDeleteKid: (String) -> Unit,
) {
    val corner = MeTheme.borderRadius.sm
    val lastIndex = count - 1
    // Round the revealed delete action's outer corners to match the list card
    // (first row -> top, last row -> bottom), like AppUserList.
    val actionShape = when {
        count == 1 -> RoundedCornerShape(topEnd = corner + 2.dp, bottomEnd = corner + 2.dp)
        index == 0 -> RoundedCornerShape(topEnd = corner + 2.dp)
        index == lastIndex -> RoundedCornerShape(bottomEnd = corner + 2.dp)
        else -> RectangleShape
    }
    AppSwipeableListItem(
        onActionOpened = onActionOpened,
        isSwipeable = true,
        index = index,
        iconWidth = 56.dp,
        showAction = isOpen,
        actionContent = {
            AppSwipeableListActions(shape = actionShape) {
                AppSwipeableActionItem(
                    iconId = AppIcons.Default.Delete,
                    contentDescription = deleteContentDescription,
                    backgroundColor = MeTheme.colorScheme.danger,
                ) {
                    onDeleteKid(kid.id)
                }
            }
        },
    ) { progress ->
        // While dragging, square the corners so the row meets the red action cleanly;
        // at rest, round the card's outer corners by row position (mirrors AppUserList).
        val r = if (progress > 0f) 0.dp else corner
        val rowShape = when {
            count == 1 -> RoundedCornerShape(r)
            index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
            index == lastIndex -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
            else -> RectangleShape
        }
        Column(
            modifier = Modifier
                .clip(rowShape)
                .background(MeTheme.colorScheme.primaryBackground, rowShape),
        ) {
            BaseListItem(
                title = kid.name,
                leadingContent = { AppProfileAvatar(text = kid.name, isActive = false) },
                trailingContent = {
                    IconButton(onClick = { onEditKid(kid.id) }) {
                        Icon(
                            painter = painterResource(AppIcons.Default.EditPencil),
                            contentDescription = editContentDescription,
                            tint = MeTheme.colorScheme.textBody,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
            if (index < lastIndex) {
                HorizontalDivider(color = MeTheme.colorScheme.utility, thickness = 0.5.dp)
            }
        }
    }
}
