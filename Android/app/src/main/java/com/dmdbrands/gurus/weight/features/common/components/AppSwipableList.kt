package com.dmdbrands.gurus.weight.features.common.components

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import kotlin.math.min

// --- Constants ---
object SwipeDefaults {
    const val POSITIONAL_THRESHOLD = 0.5f
    const val VELOCITY_THRESHOLD = 100f
}

// --- Scope Interface and Implementation ---
interface SwipeableListItemScope {
    @Composable
    fun Swipeable(content: @Composable (progress: Float) -> Unit)

    @Composable
    fun Static(content: @Composable () -> Unit)
}

private class SwipeableListItemScopeImpl<T>(val item: T, val index: Int) : SwipeableListItemScope {
    private var swipeableBuilder: (@Composable (Float) -> Unit)? = null
    private var staticBuilder: (@Composable () -> Unit)? = null

    fun buildSwipeable(progress: Float): @Composable () -> Unit =
        swipeableBuilder?.let { { it(progress) } } ?: {
            // Default fallback
            Text("⚠️ No Swipeable content defined for item at index $index")
        }

    fun buildStatic(): (@Composable () -> Unit)? = staticBuilder

    @Composable
    override fun Swipeable(content: @Composable (progress: Float) -> Unit) {
        swipeableBuilder = content
    }

    @Composable
    override fun Static(content: @Composable () -> Unit) {
        staticBuilder = content
    }

    fun hasContent(): Boolean = swipeableBuilder != null || staticBuilder != null
}

// --- Main AppSwipeableList Composable ---
@Composable
fun <T> AppSwipeableList(
    items: List<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconWidth: Dp = 56.dp,
    maxVisibleItems: Int? = null,
    isItemSwipeable: (T) -> Boolean = { true },
    keySelector: (T) -> Any,
    trailingActions: @Composable RowScope.(index: Int, item: T) -> Unit = { _, _ -> },
    positionalThreshold: Float = SwipeDefaults.POSITIONAL_THRESHOLD,
    velocityThreshold: Float = SwipeDefaults.VELOCITY_THRESHOLD,
    footerContent: @Composable (() -> Unit)? = null,
    itemContent: @Composable SwipeableListItemScope.(item: T) -> Unit,
) {
    var isMultiTouch by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var measuredItemHeight by remember { mutableStateOf(0.dp) }
    var hasMeasured by remember { mutableStateOf(false) }

    val heightModifier by remember {
        derivedStateOf {
            if (maxVisibleItems != null && hasMeasured && measuredItemHeight > 0.dp && items.size > 1) {
                Modifier.height(measuredItemHeight * min(items.size, maxVisibleItems))
            } else {
                Modifier
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier.then(heightModifier).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    isMultiTouch = event.changes.size > 1
                    // ... handle single-finger swipe logic here ...
                }
            }
        }
    ) {
        itemsIndexed(items, key = { _, item -> keySelector(item) }) { index, item ->
            // Use remember to cache the scope but ensure content is refreshed
            val scope = remember(item) { SwipeableListItemScopeImpl(item, index) }

            // Always execute the item content to ensure state changes are captured
            scope.itemContent(item)

            AppSwipeableListItem(
              actionContent = { trailingActions(index, item) },
              isSwipeable = !lazyListState.isScrollInProgress && isItemSwipeable(item) && !isMultiTouch,
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
                val swipeableContent = scope.buildSwipeable(progress)
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
                        swipeableContent()
                    }
                } else {
                    swipeableContent()
                }
            }

            scope.buildStatic()?.invoke()
        }

        footerContent?.let {
            item {
                footerContent()
            }
        }
    }
}

@PreviewTheme
@Composable
private fun PreviewAppSwipeableList() {
    MeAppTheme {
        val items = listOf("Item 1", "Item 2", "Item 3")
        AppSwipeableList(
            items = items,
            itemContent = { item ->
                Swipeable {
                    Text("Swipeable: $item")
                }
                Static {
                    Text("Static: $item")
                }
            },
            keySelector = { it },
            trailingActions = { _, item ->
                AppSwipeableListActions {
                    AppIcon(
                      id = AppIcons.Default.Delete,
                      contentDescription = "Delete",
                      modifier = Modifier,
                      type = AppIconType.Inverse,
                    )
                }
            },
        )
    }
}
