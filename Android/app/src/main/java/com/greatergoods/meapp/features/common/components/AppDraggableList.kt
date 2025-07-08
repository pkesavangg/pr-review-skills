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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import kotlin.math.min
import android.util.Log

// --- Constants ---
object DragDefaults {
    const val POSITIONAL_THRESHOLD = 0.5f
    const val VELOCITY_THRESHOLD = 100f
}

// --- Scope Interface and Implementation ---
interface DraggableListItemScope {
    @Composable
    fun Draggable(content: @Composable (progress: Float) -> Unit)

    @Composable
    fun Static(content: @Composable () -> Unit)
}

private class DraggableListItemScopeImpl<T>(val item: T, val index: Int) : DraggableListItemScope {
    private var draggableBuilder: (@Composable (Float) -> Unit)? = null
    private var staticBuilder: (@Composable () -> Unit)? = null
    var initialized: Boolean by mutableStateOf(false)

    fun buildDraggable(progress: Float): @Composable () -> Unit =
        draggableBuilder?.let { { it(progress) } } ?: {
            // Default fallback
            Text("⚠️ No Draggable content defined for item at index $index")
        }

    fun buildStatic(): (@Composable () -> Unit)? = staticBuilder

    @Composable
    override fun Draggable(content: @Composable (progress: Float) -> Unit) {
        draggableBuilder = content
    }

    @Composable
    override fun Static(content: @Composable () -> Unit) {
        staticBuilder = content
    }

    fun hasContent(): Boolean = draggableBuilder != null || staticBuilder != null
}

// --- Main AppDraggableList Composable ---
@Composable
fun <T> AppDraggableList(
    items: List<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconWidth: Dp = 56.dp,
    maxVisibleItems: Int? = null,
    isItemDraggable: (T) -> Boolean = { true },
    keySelector: (T) -> Any,
    trailingActions: @Composable RowScope.(index: Int, item: T) -> Unit = { _, _ -> },
    positionalThreshold: Float = DragDefaults.POSITIONAL_THRESHOLD,
    velocityThreshold: Float = DragDefaults.VELOCITY_THRESHOLD,
    footerContent: @Composable (() -> Unit)? = null,
    itemContent: @Composable DraggableListItemScope.(item: T) -> Unit,
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
                    // ... handle single-finger drag logic here ...
                }
            }
        }
    ) {
        itemsIndexed(items, key = { _, item -> keySelector(item) }) { index, item ->
            val scope = remember(item) { DraggableListItemScopeImpl(item, index) }

            // Run composable scope initializer
            if (!scope.initialized) {
                scope.itemContent(item)
                scope.initialized = true

                if (!scope.hasContent()) {
                    Log.w("AppDraggableList", "⚠️ No Draggable or Static scope defined for item at index $index")
                }
            }

            AppDraggableListItem(
                actionContent = { trailingActions(index, item) },
                isDraggable = !lazyListState.isScrollInProgress && isItemDraggable(item) && !isMultiTouch,
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
                val draggableContent = scope.buildDraggable(progress)
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
                        draggableContent()
                    }
                } else {
                    draggableContent()
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
private fun PreviewAppDraggableList() {
    MeAppTheme {
        val items = listOf("Item 1", "Item 2", "Item 3")
        AppDraggableList(
            items = items,
            itemContent = { item ->
                Draggable {
                    Text("Draggable: $item")
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
