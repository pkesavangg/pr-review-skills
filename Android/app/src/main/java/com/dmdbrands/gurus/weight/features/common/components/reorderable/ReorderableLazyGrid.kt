/*
 * Copyright 2023 Calvin Liang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dmdbrands.gurus.weight.features.common.components.reorderable

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope

/**
 * Creates a [ReorderableLazyGridState] that is remembered across compositions.
 *
 * Changes to [lazyGridState], [scrollThresholdPadding], [scrollThreshold], and [scroller] will result in [ReorderableLazyGridState] being updated.
 *
 * @param lazyGridState The return value of [rememberLazyGridState](androidx.compose.foundation.lazy.LazyGridStateKt.rememberLazyGridState)
 * @param scrollThresholdPadding The padding that will be added to the top and bottom, or start and end of the grid to determine the scrollThreshold. Useful for when the grid is displayed under the navigation bar or notification bar.
 * @param scrollThreshold The distance in dp from the top and bottom, or start and end of the grid that will trigger scrolling
 * @param scroller The [sh.calvin.reorderable.Scroller] that will be used to scroll the grid. Use [rememberScroller](sh.calvin.reorderable.ScrollerKt.rememberScroller) to create a [sh.calvin.reorderable.Scroller].
 * @param onMove The function that is called when an item is moved. Make sure this function returns only after the items are moved. This suspend function is invoked with the `rememberReorderableLazyGridState` scope, allowing for async processing, if desired. Note that the scope used here is the one provided by the composition where `rememberReorderableLazyGridState` is called, for long running work that needs to outlast `rememberReorderableLazyGridState` being in the composition you should use a scope that fits the lifecycle needed.
 */
@Composable
fun rememberReorderableLazyGridState(
  lazyGridState: LazyGridState,
  scrollThresholdPadding: PaddingValues = PaddingValues(0.dp),
  scrollThreshold: Dp = ReorderableLazyCollectionDefaults.ScrollThreshold,
  scroller: sh.calvin.reorderable.Scroller = _root_ide_package_.sh.calvin.reorderable.rememberScroller(
    scrollableState = lazyGridState,
    pixelAmountProvider = { lazyGridState.layoutInfo.mainAxisViewportSize * ScrollAmountMultiplier },
  ),
  onMove: suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit,
): ReorderableLazyGridState {
  val density = LocalDensity.current
  val scrollThresholdPx = with(density) { scrollThreshold.toPx() }

  val scope = rememberCoroutineScope()
  val onMoveState = rememberUpdatedState(onMove)
  val layoutDirection = LocalLayoutDirection.current
  val absoluteScrollThresholdPadding = AbsolutePixelPadding(
    start = with(density) {
      scrollThresholdPadding.calculateStartPadding(layoutDirection).toPx()
    },
    end = with(density) {
      scrollThresholdPadding.calculateEndPadding(layoutDirection).toPx()
    },
    top = with(density) { scrollThresholdPadding.calculateTopPadding().toPx() },
    bottom = with(density) { scrollThresholdPadding.calculateBottomPadding().toPx() },
  )
  val state = remember(
    scope, lazyGridState, scrollThreshold, scrollThresholdPadding, scroller,
  ) {
    ReorderableLazyGridState(
      state = lazyGridState,
      scope = scope,
      onMoveState = onMoveState,
      scrollThreshold = scrollThresholdPx,
      scrollThresholdPadding = absoluteScrollThresholdPadding,
      scroller = scroller,
      layoutDirection = layoutDirection,
    )
  }
  return state
}

private val LazyGridLayoutInfo.mainAxisViewportSize: Int
  get() = when (orientation) {
    Orientation.Vertical -> viewportSize.height
    Orientation.Horizontal -> viewportSize.width
  }

private fun LazyGridItemInfo.toLazyCollectionItemInfo() =
  object : LazyCollectionItemInfo<LazyGridItemInfo> {
    override val index: Int
      get() = this@toLazyCollectionItemInfo.index
    override val key: Any
      get() = this@toLazyCollectionItemInfo.key
    override val offset: IntOffset
      get() = this@toLazyCollectionItemInfo.offset
    override val size: IntSize
      get() = this@toLazyCollectionItemInfo.size
    override val data: LazyGridItemInfo
      get() = this@toLazyCollectionItemInfo
  }

private fun LazyGridLayoutInfo.toLazyCollectionLayoutInfo() =
  object : LazyCollectionLayoutInfo<LazyGridItemInfo> {
    override val visibleItemsInfo: List<LazyCollectionItemInfo<LazyGridItemInfo>>
      get() = this@toLazyCollectionLayoutInfo.visibleItemsInfo.map {
        it.toLazyCollectionItemInfo()
      }
    override val viewportSize: IntSize
      get() = this@toLazyCollectionLayoutInfo.viewportSize
    override val orientation: Orientation
      get() = this@toLazyCollectionLayoutInfo.orientation
    override val reverseLayout: Boolean
      get() = this@toLazyCollectionLayoutInfo.reverseLayout
    override val beforeContentPadding: Int
      get() = this@toLazyCollectionLayoutInfo.beforeContentPadding
  }

private fun LazyGridState.toLazyCollectionState() =
  object : LazyCollectionState<LazyGridItemInfo> {
    override val firstVisibleItemIndex: Int
      get() = this@toLazyCollectionState.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int
      get() = this@toLazyCollectionState.firstVisibleItemScrollOffset
    override val layoutInfo: LazyCollectionLayoutInfo<LazyGridItemInfo>
      get() = this@toLazyCollectionState.layoutInfo.toLazyCollectionLayoutInfo()

    override suspend fun animateScrollBy(value: Float, animationSpec: AnimationSpec<Float>) =
      this@toLazyCollectionState.animateScrollBy(value, animationSpec)

    override suspend fun requestScrollToItem(index: Int, scrollOffset: Int) =
      this@toLazyCollectionState.requestScrollToItem(index, scrollOffset)
  }

@Stable
class ReorderableLazyGridState internal constructor(
  state: LazyGridState,
  scope: CoroutineScope,
  onMoveState: State<suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit>,

  /**
   * The threshold in pixels for scrolling the grid when dragging an item.
   * If the dragged item is within this threshold of the top or bottom of the grid, the grid will scroll.
   * Must be greater than 0.
   */
  scrollThreshold: Float,
  scrollThresholdPadding: AbsolutePixelPadding,
  scroller: sh.calvin.reorderable.Scroller,
  layoutDirection: LayoutDirection,
) : ReorderableLazyCollectionState<LazyGridItemInfo>(
  state.toLazyCollectionState(),
  scope,
  onMoveState,
  scrollThreshold,
  scrollThresholdPadding,
  scroller,
  layoutDirection,
)

/**
 * A composable that allows an item in LazyVerticalGrid or LazyHorizontalGrid to be reordered by dragging.
 *
 * @param state The return value of [rememberReorderableLazyGridState]
 * @param key The key of the item, must be the same as the key passed to [LazyGridScope.item](androidx.compose.foundation.lazy.grid.item), [LazyGridScope.items](androidx.compose.foundation.lazy.grid.items) or similar functions in [LazyGridScope](androidx.compose.foundation.lazy.grid.LazyGridScope)
 * @param enabled Whether or this item is reorderable. If true, the item will not move for other items but may still be draggable. To make an item not draggable, set `enable = false` in [sh.calvin.reorderable.draggable] or [Modifier.longPressDraggable] instead.
 * @param animateItemModifier The [Modifier] that will be applied to items that are not being dragged.
 * @param dragThresholdPadding The padding threshold applied only when dragging to prevent items from being dragged too close to edges.
 */

@Composable
fun LazyGridItemScope.ReorderableItem(
  state: ReorderableLazyGridState,
  key: Any,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  animateItemModifier: Modifier = Modifier.animateItem(
    fadeInSpec = null,
    fadeOutSpec = null,
  ),
  content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
) {
  val dragging by state.isItemDragging(key)
  var itemSize by remember { mutableStateOf(IntSize.Zero) }
  val viewportSize = state.viewportSize
  val draggingOrigin = state.draggingItemOrigin
  val draggingSize = state.draggingItemSize ?: itemSize
  val offsetModifier = if (dragging) {
    Modifier
      .onGloballyPositioned { itemSize = it.size }
      .zIndex(1f)
      .graphicsLayer {
        // Clamp translation so the item cannot be dragged outside the grid (all sides)
        val originX = draggingOrigin?.x?.toFloat() ?: 0f
        val originY = draggingOrigin?.y?.toFloat() ?: 0f
        val maxX = (viewportSize.width - draggingSize.width).toFloat()
        val maxY = (viewportSize.height - draggingSize.height).toFloat()
        // Ensure bounds are valid (non-negative)
        val clampedMaxX = maxX.coerceAtLeast(0f)
        val clampedMaxY = maxY.coerceAtLeast(0f)
        val minTranslationX = -originX
        val maxTranslationX = clampedMaxX - originX
        val minTranslationY = -originY
        val maxTranslationY = clampedMaxY - originY - draggingSize.height.div(3)
        translationX = state.draggingItemOffset.x.coerceIn(minTranslationX, maxTranslationX)
        translationY = state.draggingItemOffset.y.coerceIn(minTranslationY, maxTranslationY)
      }
  } else if (key == state.previousDraggingItemKey) {
    Modifier
      .onGloballyPositioned { itemSize = it.size }
      .zIndex(1f)
      .graphicsLayer {
        val originX = draggingOrigin?.x?.toFloat() ?: 0f
        val maxX = (viewportSize.width - draggingSize.width).toFloat()
        // Ensure bounds are valid (non-negative)
        val clampedMaxX = maxX.coerceAtLeast(0f)
        val minTranslationX = -originX
        val maxTranslationX = clampedMaxX - originX
        translationX = state.previousDraggingItemOffset.value.x.coerceIn(minTranslationX, maxTranslationX)
      }
  } else {
    Modifier.onGloballyPositioned { itemSize = it.size } then if (enabled) animateItemModifier else Modifier
  }

  ReorderableCollectionItem(
    state = state,
    key = key,
    modifier = modifier.then(offsetModifier),
    enabled = enabled,
    dragging = dragging,
    content = content,
  )
}
