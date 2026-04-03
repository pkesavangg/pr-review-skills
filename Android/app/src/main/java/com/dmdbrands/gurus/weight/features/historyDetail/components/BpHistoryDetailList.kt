package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getDate
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.getTime

/**
 * List of BP history detail entries for a specific month.
 */
@Composable
fun BpHistoryDetailList(
    entries: List<BpmEntry>,
    expandedIds: List<Long>,
    onToggleExpand: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(entries, key = { it.entry.id }) { entry ->
            BpHistoryDetailItem(
                entry = entry,
                dateDisplay = entry.getDate(),
                timeDisplay = entry.getTime(),
                isExpanded = expandedIds.contains(entry.entry.id),
                onToggle = { onToggleExpand(entry.entry.id) },
            )
        }
    }
}
