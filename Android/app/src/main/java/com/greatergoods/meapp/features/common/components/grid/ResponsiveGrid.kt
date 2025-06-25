package com.greatergoods.meapp.features.common.components.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A composable that displays items in a responsive grid.
 *
 * @param T The type of the items in the list.
 * @param items The list of items to display.
 * @param columns The number of columns in the grid.
 * @param modifier The modifier to apply to this layout.
 * @param itemContent The composable content for each item in the grid.
 */
@Composable
fun <T> ResponsiveGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int, T) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val itemWidth = maxWidth / columns
        Column {
            items.chunked(columns).forEach { rowItems ->
                Row {
                    rowItems.forEachIndexed { index, item ->
                        Box(modifier = Modifier.width(itemWidth)) {
                            itemContent(index, item)
                        }
                    }
                }
            }
        }
    }
}
