package com.dmdbrands.gurus.weight.features.historyDetail.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry

/**
 * List of baby day history entries with expandable notes.
 */
@Composable
fun BabyDayHistoryList(
    entries: List<BabyEntry>,
    isMetric: Boolean = false,
    onEditEntry: (BabyEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val expandedIndices = remember { mutableStateListOf<Int>() }

    LazyColumn(modifier = modifier) {
        itemsIndexed(entries, key = { _, item -> item.entry.id }) { index, item ->
            BabyDayHistoryItem(
                item = item,
                isMetric = isMetric,
                isExpanded = expandedIndices.contains(index),
                onToggleExpand = {
                    if (expandedIndices.contains(index)) {
                        expandedIndices.remove(index)
                    } else {
                        expandedIndices.add(index)
                    }
                },
                onEditEntry = { onEditEntry(item) },
            )
        }
    }
}
