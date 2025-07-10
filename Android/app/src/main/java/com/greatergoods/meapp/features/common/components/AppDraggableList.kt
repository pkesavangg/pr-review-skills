package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// --- Scope Interface and Implementation ---
interface DraggableListItemScope {
    @Composable
    fun DraggableItem(
        isDraggable: Boolean = true,
        content: @Composable (isDragging: Boolean) -> Unit
    )

    @Composable
    fun StaticItem(content: @Composable () -> Unit)
}

private class DraggableListItemScopeImpl<T>(
  val item: T,
  val index: Int,
) : DraggableListItemScope {
  private var draggableBuilder: (@Composable (Boolean) -> Unit)? = null
  private var staticBuilder: (@Composable () -> Unit)? = null
  private var isDraggableItem: Boolean = true
  var initialized: Boolean by mutableStateOf(false)

  fun buildDraggable(isDragging: Boolean): @Composable () -> Unit =
    draggableBuilder?.let { { it(isDragging) } } ?: {
      // Default fallback
      Text("⚠️ No DraggableItem content defined for item at index $index")
    }

  fun buildStatic(): (@Composable () -> Unit)? = staticBuilder

  fun isDraggable(): Boolean = isDraggableItem

  @Composable
  override fun DraggableItem(
    isDraggable: Boolean,
    content: @Composable (isDragging: Boolean) -> Unit
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
  verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
  keySelector: (T) -> Any,
  onDragStarted: () -> Unit = {},
  onDragStopped: () -> Unit = {},
  itemContent: @Composable DraggableListItemScope.(item: T) -> Unit,
) {
  val lazyListState = rememberLazyListState()
  val hapticFeedback = LocalHapticFeedback.current

  val reorderableState =
    rememberReorderableLazyListState(
      lazyListState = lazyListState,
      onMove = { from, to ->
        onMove(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
      },
    )

  LazyColumn(
    state = lazyListState,
    contentPadding = contentPadding,
    verticalArrangement = verticalArrangement,
    modifier = modifier,
  ) {
    items(
      items = items,
      key = { item -> keySelector(item) },
    ) { item ->
      val scope = remember(item) { DraggableListItemScopeImpl(item, items.indexOf(item)) }

      // Run composable scope initializer
      if (!scope.initialized) {
        scope.itemContent(item)
        scope.initialized = true

        if (!scope.hasContent()) {
          android.util.Log.w("AppDraggableList", "⚠️ No DraggableItem or StaticItem scope defined for item")
        }
      }

              ReorderableItem(
          state = reorderableState,
          key = keySelector(item),
        ) { isDragging ->
          Column {
            // Draggable content
            val draggableContent = scope.buildDraggable(isDragging)

            Box(
              modifier = if (scope.isDraggable()) {
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
            ) {
              draggableContent()
            }

            // Static content (if any)
            scope.buildStatic()?.invoke()
          }
        }
    }
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
            isDraggable = item != "Body Fat" // Example: Body Fat is not draggable
          ) { isDragging ->
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
          DraggableItem { isDragging ->
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
