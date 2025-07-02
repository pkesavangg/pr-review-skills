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

enum class StaticPosition {
    Top,
    Bottom
}

/**
 * Scope for defining draggable and static content in a draggable list item.
 */
interface DraggableListItemScope {
    @Composable
    fun Draggable(content: @Composable () -> Unit)

    @Composable
    fun Static(position: StaticPosition = StaticPosition.Bottom, content: @Composable () -> Unit)
}

private class DraggableListItemScopeImpl : DraggableListItemScope {
    var staticPosition: StaticPosition = StaticPosition.Bottom
    var draggableContent: (@Composable () -> Unit)? = null
    var staticContent: (@Composable () -> Unit)? = null

    @Composable
    override fun Draggable(
        content: @Composable (() -> Unit)
    ) {
        draggableContent = content
    }

    @Composable
    override fun Static(
        position: StaticPosition,
        content: @Composable (() -> Unit)
    ) {
        staticPosition = position
        staticContent = content
    }
}

/**
 * Displays a vertically scrollable, draggable list where each item can have a draggable and a static (undraggable) part.
 *
 * @param items The list of items to display.
 * @param modifier Modifier for the LazyColumn.
 * @param contentPadding Padding for the list content.
 * @param iconWidth The width of the action icon area.
 * @param itemContent Scoped composable lambda for rendering each item. Use Draggable { ... } and Static { ... } in the scope.
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
    itemContent: @Composable DraggableListItemScope.(item: T, progress: Float) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var measuredItemHeight by remember { mutableStateOf(0.dp) }
    var hasMeasured by remember { mutableStateOf(false) }

    val heightModifier by remember {
        derivedStateOf {
            if (maxVisibleItems != null && hasMeasured && measuredItemHeight > 0.dp) {
                Modifier.height(measuredItemHeight * minOf(items.size, maxVisibleItems))
            } else {
                Modifier
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier.then(heightModifier),
    ) {
        itemsIndexed(items, key = { _, item -> keySelector(item) }) { index, item ->
            // Create a single scope instance that will be used to collect both contents
            val scope = remember(item) { DraggableListItemScopeImpl() }

            // If neither Draggable nor Static was called, treat the entire content as draggable
            val noExplicitContent = scope.draggableContent == null && scope.staticContent == null

            if (scope.staticPosition == StaticPosition.Top) {
                scope.staticContent?.invoke()
            }
            // Render draggable content (either explicit or fallback)
            if (scope.draggableContent != null || noExplicitContent) {
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

                    if (index == 0 && !hasMeasured) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val height = with(density) { coordinates.size.height.toDp() }
                                if (height > 0.dp) {
                                    measuredItemHeight = height
                                    hasMeasured = true
                                }
                            },
                        ) {
                            scope.draggableContent?.invoke()
                        }
                    } else {
                        scope.draggableContent?.invoke()
                    }
                    scope.itemContent(item, progress)
                }
            }
            if (scope.staticPosition == StaticPosition.Bottom) {
                scope.staticContent?.invoke()
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
            itemContent = { item, progress ->
                Draggable {
                    Text("Draggable: $item (progress: $progress)")
                }
                Static {
                    Text("Static: $item")
                }
            },
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
