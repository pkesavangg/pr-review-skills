package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * List of BP history items grouped by month.
 */
@Composable
fun BpHistoryList(
    items: List<BpHistoryMonth>,
    onItemClick: (BpHistoryMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        // Index-composite key: the month label alone can collide (duplicate/edge-case timestamps
        // in the data can render two rows as the same "MMM yyyy"), which crashes LazyColumn with a
        // "key already used" error. The index guarantees uniqueness regardless of the data.
        itemsIndexed(
            items,
            // Tradeoff: folding index into the key sacrifices stable item identity (inserting or
            // removing a month re-renders every row below it) in exchange for crash-safety — see PR #2291 review.
            key = { index, item -> historyRowKey(item.entryTimestamp, index) },
        ) { _, item ->
            BpHistoryItem(item = item, onClick = { onItemClick(item) })
            HorizontalDivider(
                thickness = MeTheme.spacing.x6s,
                color = MeTheme.colorScheme.utility,
            )
        }
    }
}
