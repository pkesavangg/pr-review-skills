package com.dmdbrands.gurus.weight.features.history.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        items(items, key = { it.entryTimestamp }) { item ->
            BpHistoryItem(item = item, onClick = { onItemClick(item) })
            HorizontalDivider(
                thickness = MeTheme.spacing.x6s,
                color = MeTheme.colorScheme.utility,
            )
        }
    }
}
