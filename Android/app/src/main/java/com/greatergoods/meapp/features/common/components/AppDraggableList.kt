package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme

object DragDefaults {
    const val POSITIONAL_THRESHOLD = 0.5f
    const val VELOCITY_THRESHOLD = 100f
}

/**
 * Displays a vertically scrollable, draggable list where each item can reveal one or more actions via swipe.
 *
 * @param items The list of items to display.
 * @param modifier Modifier for the LazyColumn.
 * @param contentPadding Padding for the list content.
 * @param iconWidth The width of the action icon area.
 * @param itemContent Composable lambda for rendering each item. Receives the item and swipe progress.
 * @param keySelector Lambda to provide a unique, stable key for each item.
 * @param trailingActions Composable lambda for rendering trailing actions for each item.
 * @param positionalThreshold Fraction of iconWidth to trigger open/close (default: 0.5f).
 * @param velocityThreshold Velocity threshold for swipe (default: 100f).
 * @param maxVisibleItems Maximum number of items to display in the viewport. If null, all items are displayed.
 * @param isItemDraggable Lambda to determine if an item is draggable.
 */
@Composable
fun <T> AppDraggableList(
    items: List<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconWidth: Dp = 56.dp,
    maxVisibleItems: Int? = null,
    isItemDraggable: (T) -> Boolean = { true },
    keySelector: (T) -> Any,
    trailingActions: @Composable RowScope.(index: Int, item: T) -> Unit,
    positionalThreshold: Float = DragDefaults.POSITIONAL_THRESHOLD,
    velocityThreshold: Float = DragDefaults.VELOCITY_THRESHOLD,
    footerContent: @Composable (() -> Unit)? = null,
    itemContent: @Composable (item: T, progress: Float) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var measuredItemHeight by remember { mutableStateOf(0.dp) }
    var hasMeasured by remember { mutableStateOf(false) }

    val heightModifier by derivedStateOf {
        if (maxVisibleItems != null && hasMeasured && measuredItemHeight > 0.dp) {
            Modifier.height(measuredItemHeight * minOf(items.size, maxVisibleItems))
        } else {
            Modifier
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier.then(heightModifier),
    ) {
        itemsIndexed(items, key = { _, item -> keySelector(item) }) { index, item ->
            AppDraggableListItem(
                actionContent = {
                    trailingActions(index, item)
                },
                isDraggable = !lazyListState.isScrollInProgress && isItemDraggable(item),
                iconWidth = iconWidth,
                index = index,
                onActionOpened = { openedIdx ->
                    if (openIndex != openedIdx) {
                        openIndex = openedIdx
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                showAction = openIndex == index,
                positionalThreshold = positionalThreshold,
                velocityThreshold = velocityThreshold,
            ) { progress ->
                // Measure the first item to determine item height
                if (index == 0 && !hasMeasured) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val height = with(density) { coordinates.size.height.toDp() }
                            if (height > 0.dp) {
                                measuredItemHeight = height
                                hasMeasured = true
                            }
                        }
                    ) {
                        itemContent(item, progress)
                    }
                } else {
                    itemContent(item, progress)
                }
            }
        }
        footerContent?.let {
            item {
                footerContent()
            }
        }
    }
}

// region: Previews

@PreviewTheme
@Composable
private fun PreviewAppDraggableList() {
    MeAppTheme {
        val items = listOf("Item 1", "Item 2", "Item 3")
        AppDraggableList(
            items = items,
            itemContent = { item, _ -> Text(item) },
            keySelector = { it },
            trailingActions = { _, item ->
                AppDraggableListActions {
                    AppIcon(
                        id = AppIcons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier,
                        type = AppIconType.Secondary,
                    )
                }
            },
        )
    }
}
// endregion
