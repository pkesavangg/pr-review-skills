package com.greatergoods.meapp.features.history.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * List of history items, using HistoryItem for each row.
 * @param items List of history item models
 * @param onItemClick Callback when an item is clicked
 */
@Composable
fun HistoryList(
    items: List<HistoryItemModel>,
    onItemClick: (HistoryItemModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items) { item ->
            HistoryItem(item = item, onClick = { onItemClick(item) })
        }
    }
}

@PreviewTheme
@Composable
fun HistoryListPreview() {
    MeAppTheme {
        val sampleItems =
            listOf(
                HistoryItemModel("Weight", "Morning Weigh-in", "Apr 20, 2024", "180 lbs"),
                HistoryItemModel("Steps", "Afternoon Walk", "Apr 19, 2024", "10,000"),
                HistoryItemModel("Sleep", "Last Night", "Apr 19, 2024", "7h 30m"),
            )
        HistoryList(items = sampleItems, onItemClick = {})
    }
}
