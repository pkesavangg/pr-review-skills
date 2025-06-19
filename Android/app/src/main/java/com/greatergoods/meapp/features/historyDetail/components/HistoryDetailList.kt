package com.greatergoods.meapp.features.historyDetail.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * List of history detail items, using HistoryDetailItem for each row.
 * @param items List of history detail item models
 * @param onItemClick Callback when an item is clicked
 */
@Composable
fun HistoryDetailList(
    items: List<HistoryDetailItemModel>,
    onItemClick: (HistoryDetailItemModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items) { item ->
            HistoryDetailItem(
                item = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@PreviewTheme
@Composable
fun HistoryDetailListPreview() {
    MeAppTheme {
        val sampleItems =
            listOf(
                HistoryDetailItemModel(
                    date = "Dec 16",
                    time = "2:10 PM",
                    weight = "149.2",
                ),
                HistoryDetailItemModel(
                    date = "Dec 10",
                    time = "2:10 PM",
                    weight = "148.7",
                ),
            )
        HistoryDetailList(
            items = sampleItems,
            onItemClick = {},
        )
    }
}
