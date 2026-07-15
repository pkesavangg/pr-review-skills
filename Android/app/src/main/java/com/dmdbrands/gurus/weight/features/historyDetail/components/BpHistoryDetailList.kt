package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableActionItem
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableList
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListActions
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getDate
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getTime
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * List of BP history detail entries for a specific month. Each row swipes left to reveal a
 * Delete action ([onItemDelete]) — mirrors the weight history list.
 */
@Composable
fun BpHistoryDetailList(
    entries: List<BpmEntry>,
    expandedIds: List<Long>,
    onToggleExpand: (Long) -> Unit,
    onEditEntry: (BpmEntry) -> Unit = {},
    onItemDelete: (BpmEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                ) {
                    onItemDelete(item)
                }
            }
        },
    ) { entry ->
        // Wrap the row in Swipeable so it reveals the Delete trailingAction on left-swipe
        // (mirrors the weight/baby lists). Without this the swipe layer has no content and the
        // list renders its "No Swipeable content defined" fallback, so BP entries can't be
        // deleted. (MOB-1462; same fix MOB-634 applied to the baby list.)
        Swipeable {
            BpHistoryDetailItem(
                entry = entry,
                dateDisplay = entry.getDate(),
                timeDisplay = entry.getTime(),
                isExpanded = expandedIds.contains(entry.entry.id),
                onToggle = { onToggleExpand(entry.entry.id) },
                onEditEntry = { onEditEntry(entry) },
            )
        }
    }
}
