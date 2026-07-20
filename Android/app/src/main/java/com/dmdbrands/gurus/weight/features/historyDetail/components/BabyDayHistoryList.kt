package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import androidx.compose.ui.platform.testTag
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableActionItem
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableList
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListActions
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * List of baby day history entries with expandable notes. Each row swipes left to reveal a
 * Delete action ([onItemDelete]) — mirrors the weight history list.
 */
@Composable
fun BabyDayHistoryList(
    entries: List<BabyEntry>,
    babyWeightUnit: WeightUnit = WeightUnit.LB_OZ,
    onEditEntry: (BabyEntry) -> Unit = {},
    onItemDelete: (BabyEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Track expansion by entry id (the swipeable list yields the item, not its index).
    val expandedIds = remember { mutableStateListOf<Long>() }

    AppSwipeableList(
        items = entries,
        modifier = modifier,
        iconWidth = 88.dp,
        keySelector = { it.entry.id },
        trailingActions = { _, item ->
            AppSwipeableListActions {
                AppSwipeableActionItem(
                    itemWidth = 88.dp,
                    text = HistoryDetailScreenStrings.DeleteButton,
                    contentDescription = HistoryDetailScreenStrings.DeleteEntryContentDescription,
                    // Destructive swipe fill uses the Status/danger background token, not the
                    // text/error token (same red, correct role) — matches other lists. (MOB-1259)
                    backgroundColor = MeTheme.colorScheme.danger,
                    modifier = Modifier.testTag(TestTags.History.DeleteButton),
                ) {
                    onItemDelete(item)
                }
            }
        },
    ) { item ->
        val id = item.entry.id
        // Wrap the row in Swipeable so it reveals the Delete trailingAction on left-swipe
        // (mirrors the weight/BP lists). Without this the swipe layer has no content and the
        // list renders its "No Swipeable content defined" fallback.
        Swipeable {
            BabyDayHistoryItem(
                item = item,
                babyWeightUnit = babyWeightUnit,
                isExpanded = expandedIds.contains(id),
                onToggleExpand = {
                    if (expandedIds.contains(id)) expandedIds.remove(id) else expandedIds.add(id)
                },
                onEditEntry = { onEditEntry(item) },
            )
        }
    }
}
