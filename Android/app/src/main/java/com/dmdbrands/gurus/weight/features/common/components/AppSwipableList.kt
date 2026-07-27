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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme

// --- Constants ---
object SwipeDefaults {
    const val POSITIONAL_THRESHOLD = 0.5f
    const val VELOCITY_THRESHOLD = 100f

    /**
     * Fallback per-item height used to bound the list to [maxVisibleItems] on the very first
     * layout pass, before an item has been measured. Keeping a sensible estimate here means the
     * height cap is applied deterministically from frame one, so overflowing lists never expand
     * and push surrounding content (e.g. landing-screen CTAs) off-screen. Approximates an
     * AppUser row: sm padding top + ~44dp of two-line content + sm padding bottom.
     */
    val ESTIMATED_ITEM_HEIGHT: Dp = 76.dp
}

/**
 * Pure calculation of the max height a swipeable list should be constrained to so that at most
 * [maxVisibleItems] items are visible before scrolling.
 *
 * @return the capped height, or `null` when no cap should be applied (list wraps its content).
 *   A cap is only applied when [maxVisibleItems] is set and [itemCount] exceeds it. Before an item
 *   is measured ([measuredItemHeight] == 0.dp) the [estimatedItemHeight] is used so the bound is
 *   in place from the first layout pass; once measured, the exact height refines it.
 */
internal fun computeListMaxHeight(
    maxVisibleItems: Int?,
    itemCount: Int,
    measuredItemHeight: Dp,
    estimatedItemHeight: Dp = SwipeDefaults.ESTIMATED_ITEM_HEIGHT,
): Dp? {
    if (maxVisibleItems == null || maxVisibleItems <= 0 || itemCount <= maxVisibleItems) {
        return null
    }
    val perItemHeight = if (measuredItemHeight > 0.dp) measuredItemHeight else estimatedItemHeight
    return perItemHeight * maxVisibleItems
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

    // Reset measurement when items change
    LaunchedEffect(items.size, maxVisibleItems) {
        if (items.isEmpty() || maxVisibleItems == null) {
            hasMeasured = false
            measuredItemHeight = 0.dp
        }
    }

    // Cap the list to at most maxVisibleItems tile heights whenever the list overflows.
    // Computed directly on every composition (NOT a keyless remember/derivedStateOf) so it reacts
    // to the asynchronously-loaded [items] list: a keyless derivedStateOf captured the initial —
    // empty — items and never updated, so an async-populated list (e.g. the multi-user landing's
    // accounts) was never capped and overflowed past maxVisibleItems, pushing CTAs off-screen
    // (MOB-1572). Reading measuredItemHeight (snapshot state) here still recomposes once a tile is
    // measured so the estimate refines to the exact height. When the list fits, no cap is applied
    // so it can size naturally and stay centered.
    val heightModifier = computeListMaxHeight(
        maxVisibleItems = maxVisibleItems,
        itemCount = items.size,
        measuredItemHeight = measuredItemHeight,
    )?.let { Modifier.height(it) } ?: Modifier

    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier.then(heightModifier).trackMultiTouch { isMultiTouch = it },
    ) {
        itemsIndexed(items, key = { _, item -> keySelector(item) }) { index, item ->
            AppSwipeableRow(
                item = item,
                index = index,
                iconWidth = iconWidth,
                isSwipeable = !lazyListState.isScrollInProgress && isItemSwipeable(item) && !isMultiTouch,
                positionalThreshold = positionalThreshold,
                velocityThreshold = velocityThreshold,
                hasMeasured = hasMeasured,
                maxVisibleItems = maxVisibleItems,
                density = density,
                openIndex = openIndex,
                haptic = haptic,
                trailingActions = trailingActions,
                itemContent = itemContent,
                onOpenIndexChange = { openIndex = it },
                onMeasured = { height ->
                    measuredItemHeight = height
                    hasMeasured = true
                },
            )
        }

        footerContent?.let {
            item {
                footerContent()
            }
        }
    }
}

// Emits a single item row: builds the per-item scope, renders the swipeable
// item and any static content. Extracted to keep AppSwipeableList short.
@Composable
private fun <T> AppSwipeableRow(
    item: T,
    index: Int,
    iconWidth: Dp,
    isSwipeable: Boolean,
    positionalThreshold: Float,
    velocityThreshold: Float,
    hasMeasured: Boolean,
    maxVisibleItems: Int?,
    density: Density,
    openIndex: Int?,
    haptic: HapticFeedback,
    trailingActions: @Composable RowScope.(index: Int, item: T) -> Unit,
    itemContent: @Composable SwipeableListItemScope.(item: T) -> Unit,
    onOpenIndexChange: (Int?) -> Unit,
    onMeasured: (Dp) -> Unit,
) {
    // Use remember to cache the scope but ensure content is refreshed
    val scope = remember(item) { SwipeableListItemScopeImpl(item, index) }

    // Always execute the item content to ensure state changes are captured
    scope.itemContent(item)

    AppSwipeableListItem(
      actionContent = { trailingActions(index, item) },
      isSwipeable = isSwipeable,
      iconWidth = iconWidth,
      index = index,
      onActionOpened = { openedIdx ->
            if (openIndex != openedIdx) {
                onOpenIndexChange(openedIdx)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        },
      showAction = openIndex == index,
      positionalThreshold = positionalThreshold,
      velocityThreshold = velocityThreshold,
    ) { progress ->
        AppSwipeableItemContent(
            scope = scope,
            progress = progress,
            hasMeasured = hasMeasured,
            maxVisibleItems = maxVisibleItems,
            density = density,
            onMeasured = onMeasured,
        )
    }

    scope.buildStatic()?.invoke()
}

// Renders the swipeable content and, while the list height is still being
// measured, wraps it in a Box that reports the measured item height.
@Composable
private fun <T> AppSwipeableItemContent(
    scope: SwipeableListItemScopeImpl<T>,
    progress: Float,
    hasMeasured: Boolean,
    maxVisibleItems: Int?,
    density: Density,
    onMeasured: (Dp) -> Unit,
) {
    val swipeableContent = scope.buildSwipeable(progress)
    // Measure the first item that gets positioned (not just index 0)
    // This ensures measurement happens even if first item is not visible initially
    if (!hasMeasured && maxVisibleItems != null) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val height = with(density) { coordinates.size.height.toDp() }
                // Only set if we haven't measured yet and height is valid
                if (height > 0.dp && !hasMeasured) {
                    onMeasured(height)
                }
            },
        ) {
            swipeableContent()
        }
    } else {
        swipeableContent()
    }
}

// Reports whether more than one pointer is currently down so single-finger
// swipe handling can be suppressed during multi-touch gestures.
private fun Modifier.trackMultiTouch(onMultiTouch: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                onMultiTouch(event.changes.size > 1)
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
