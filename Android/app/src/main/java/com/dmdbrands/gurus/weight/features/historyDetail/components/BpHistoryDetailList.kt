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
                    backgroundColor = MeTheme.colorScheme.textError,
                ) {
                    onItemDelete(item)
                }
            }
        },
    ) { entry ->
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
