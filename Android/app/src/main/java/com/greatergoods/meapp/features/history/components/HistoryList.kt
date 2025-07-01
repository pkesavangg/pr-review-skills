package com.greatergoods.meapp.features.history.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * List of history items, using HistoryItem for each row.
 * @param items List of history item models
 * @param onItemClick Callback when an item is clicked
 */
@Composable
fun HistoryList(
    items: List<HistoryMonth>,
    onItemClick: (HistoryMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items) { item ->
            HistoryItem(item = item, onClick = { onItemClick(item) })
            HorizontalDivider(
                thickness = MeTheme.spacing.x6s,
                color = MeTheme.colorScheme.utility,
            )
        }
    }
}

@PreviewTheme
@Composable
fun HistoryListPreview() {
    MeAppTheme {
        val sampleItems =
            listOf(
                HistoryMonth(
                    entryTimestamp = "2023-10",
                    avgWeight = 70.5,
                    entryCount = 15,
                    change = -1.2,
                ),
                HistoryMonth(
                    entryTimestamp = "2023-09",
                    avgWeight = 71.0,
                    entryCount = 12,
                    change = 0.5,
                ),
                HistoryMonth(
                    entryTimestamp = "2023-08",
                    avgWeight = 72.0,
                    entryCount = 10,
                    change = -0.8,
                ),
            )
        HistoryList(items = sampleItems, onItemClick = {})
    }
}
