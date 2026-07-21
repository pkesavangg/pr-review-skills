package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ReorderableCollectionItemScope
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ScrollAmountMultiplier
import com.dmdbrands.gurus.weight.features.common.strings.AppListStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.Scroller
import sh.calvin.reorderable.mainAxisViewportSize
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberScroller
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog

// --- Scope Interface and Implementation ---
interface DraggableListItemScope {
  @Composable
  fun DraggableItem(
    isDraggable: Boolean = true,
    content: @Composable (isDragging: Boolean, modifier: Modifier) -> Unit
  )

  @Composable
  fun StaticItem(content: @Composable () -> Unit)
}

private class DraggableListItemScopeImpl<T>(
  val item: T,
  val index: Int,
) : DraggableListItemScope {
  private var draggableBuilder: (@Composable (Boolean, Modifier) -> Unit)? = null
  private var staticBuilder: (@Composable () -> Unit)? = null
  private var isDraggableItem: Boolean = true
  var initialized: Boolean by mutableStateOf(false)

  fun buildDraggable(isDragging: Boolean, modifier: Modifier): @Composable () -> Unit =
    draggableBuilder?.let { { it(isDragging, modifier) } } ?: {
      // Default fallback
      Text("⚠️ No DraggableItem content defined for item at index $index")
    }

  fun buildStatic(): (@Composable () -> Unit)? = staticBuilder

  fun isDraggable(): Boolean = isDraggableItem

  @Composable
  override fun DraggableItem(
    isDraggable: Boolean,
    content: @Composable (isDragging: Boolean, modifier: Modifier) -> Unit
  ) {
    draggableBuilder = content
    isDraggableItem = isDraggable
  }

  @Composable
  override fun StaticItem(content: @Composable () -> Unit) {
    staticBuilder = content
  }

  fun hasContent(): Boolean = draggableBuilder != null || staticBuilder != null
}

// --- Main AppDraggableList Composable ---
@Composable
fun <T> AppDraggableList(
  items: List<T>,
  onMove: (from: Int, to: Int) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  scrollState: ScrollableState? = null,
  verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
  keySelector: (T) -> Any,
  onDragStarted: () -> Unit = {},
  onDragStopped: () -> Unit = {},
  itemContent: @Composable DraggableListItemScope.(item: T) -> Unit,
) {
  val lazyListState = rememberLazyListState()
  val hapticFeedback = LocalHapticFeedback.current

  val scroller = rememberScroller(
    scrollableState = scrollState ?: lazyListState,
    pixelAmountProvider = {
      val viewportSize = when (scrollState) {
        is LazyListState -> scrollState.layoutInfo.mainAxisViewportSize
        else -> lazyListState.layoutInfo.mainAxisViewportSize
      }
      viewportSize * ScrollAmountMultiplier
    },
  )

  val reorderableState =
    rememberReorderableLazyListState(
      lazyListState = lazyListState,
      scroller = scroller,
      scrollThreshold = 140.dp,
      onMove = { from, to ->
        onMove(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
      },
    )

  LazyColumn(
    state = lazyListState,
    contentPadding = contentPadding,
    verticalArrangement = verticalArrangement,
    userScrollEnabled = false,
    modifier = modifier,
  ) {
    items(
      items = items,
      key = { item -> keySelector(item) },
    ) { item ->
      DraggableRow(
        item = item,
        items = items,
        reorderableState = reorderableState,
        hapticFeedback = hapticFeedback,
        keySelector = keySelector,
        onMove = onMove,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        itemContent = itemContent,
      )
    }
  }
}

// Builds the per-item scope, runs its initializer once and emits a
// ReorderableItem. Extracted to keep AppDraggableList short.
@Composable
private fun <T> LazyItemScope.DraggableRow(
  item: T,
  items: List<T>,
  reorderableState: ReorderableLazyListState,
  hapticFeedback: HapticFeedback,
  keySelector: (T) -> Any,
  onMove: (from: Int, to: Int) -> Unit,
  onDragStarted: () -> Unit,
  onDragStopped: () -> Unit,
  itemContent: @Composable DraggableListItemScope.(item: T) -> Unit,
) {
  val scope = remember(item) { DraggableListItemScopeImpl(item, items.indexOf(item)) }

  // Run composable scope initializer
  if (!scope.initialized) {
    scope.itemContent(item)
    scope.initialized = true

    if (!scope.hasContent()) {
      AppLog.w("AppDraggableList", "⚠️ No DraggableItem or StaticItem scope defined for item")
    }
  }

  ReorderableItem(
    state = reorderableState,
    key = keySelector(item),
  ) { isDragging ->
    DraggableRowContent(
      scope = scope,
      item = item,
      items = items,
      isDragging = isDragging,
      hapticFeedback = hapticFeedback,
      onMove = onMove,
      onDragStarted = onDragStarted,
      onDragStopped = onDragStopped,
    )
  }
}

// Renders a single reorderable row: TalkBack move actions plus the draggable
// handle wrapper around the item's draggable and static content.
@Composable
private fun <T> ReorderableCollectionItemScope.DraggableRowContent(
  scope: DraggableListItemScopeImpl<T>,
  item: T,
  items: List<T>,
  isDragging: Boolean,
  hapticFeedback: HapticFeedback,
  onMove: (from: Int, to: Int) -> Unit,
  onDragStarted: () -> Unit,
  onDragStopped: () -> Unit,
) {
  // TalkBack: drag-to-reorder is a gesture a screen-reader user can't perform.
  // Expose equivalent move-up/move-down custom actions on the row. mergeDescendants
  // makes the row a single focusable node that carries them.
  val currentIndex = items.indexOf(item)
  val reorderSemantics = if (scope.isDraggable()) {
    Modifier.semantics(mergeDescendants = true) {
      customActions = buildList {
        if (currentIndex > 0) {
          add(
            CustomAccessibilityAction(AppListStrings.accMoveUpLabel) {
              onMove(currentIndex, currentIndex - 1)
              true
            },
          )
        }
        if (currentIndex >= 0 && currentIndex < items.size - 1) {
          add(
            CustomAccessibilityAction(AppListStrings.accMoveDownLabel) {
              onMove(currentIndex, currentIndex + 1)
              true
            },
          )
        }
      }
    }
  } else {
    Modifier
  }
  Column(modifier = reorderSemantics) {
    val draggingModifier = if (scope.isDraggable()) {
      Modifier.draggableHandle(
        onDragStarted = {
          hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
          onDragStarted()
        },
        onDragStopped = {
          hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
          onDragStopped()
        },
      )
    } else {
      Modifier
    }
    // Draggable content
    val draggableContent = scope.buildDraggable(isDragging, draggingModifier)

    Box {
      draggableContent()
    }

    // Static content (if any)
    scope.buildStatic()?.invoke()
  }
}

@PreviewTheme
@Composable
private fun PreviewAppDraggableList() {
  MeAppTheme {
    val bodyItems = remember { mutableStateOf(listOf("BMI", "Body Fat", "Muscle Mass")) }
    val otherItems = remember { mutableStateOf(listOf("Goal Progress", "Daily Average")) }

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(16.dp),
    ) {
      // Body metrics section
      Text("Body Metrics", style = MeTheme.typography.heading3)
      AppDraggableList(
        items = bodyItems.value,
        onMove = { from, to ->
          val newList = bodyItems.value.toMutableList()
          newList.add(to, newList.removeAt(from))
          bodyItems.value = newList
        },
        keySelector = { "body_$it" },
        itemContent = { item ->
          DraggableItem(
            isDraggable = item != "Body Fat", // Example: Body Fat is not draggable
          ) { isDragging, modifier ->
            Text(
              text = "📊 $item ${if (isDragging) "(Dragging)" else if (item == "Body Fat") "(Disabled)" else ""}",
              modifier = Modifier.padding(16.dp),
            )
          }
        },
      )

      // Spacer
      Spacer(modifier = Modifier.height(16.dp))

      // Other metrics section
      Text("Other Metrics", style = MeTheme.typography.heading3)
      AppDraggableList(
        items = otherItems.value,
        onMove = { from, to ->
          val newList = otherItems.value.toMutableList()
          newList.add(to, newList.removeAt(from))
          otherItems.value = newList
        },
        keySelector = { "other_$it" },
        itemContent = { item ->
          DraggableItem { isDragging, modifier ->
            Text(
              text = "📈 $item ${if (isDragging) "(Dragging)" else ""}",
              modifier = Modifier.padding(16.dp),
            )
          }
        },
      )
    }
  }
}
