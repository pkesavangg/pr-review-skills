package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ReorderableItem
import com.dmdbrands.gurus.weight.features.common.components.reorderable.rememberReorderableLazyGridState
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Grid layout for displaying dashboard milestones.
 *
 * @param progress Progress data containing weight information
 * @param visibleMilestones List of currently visible milestone stats
 * @param hiddenMilestones List of hidden milestone stats
 * @param inEditMode Whether the dashboard is in edit mode
 * @param onMilestoneMoved Callback when a milestone is moved between visible and hidden states
 * @param onMilestoneReordered Callback when visible milestones are reordered
 */
@Composable
fun DashboardMilestoneGrid(
  progress: Progress,
  visibleMilestones: List<Stat>,
  hiddenMilestones: List<Stat>,
  inEditMode: Boolean,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onMilestoneReordered: (List<Stat>) -> Unit,
) {
  var localVisibleMilestones by remember(visibleMilestones) { mutableStateOf(visibleMilestones) }
  val hapticFeedback = LocalHapticFeedback.current
  val lazyGridState = rememberLazyGridState()

  val reorderableState = rememberReorderableLazyGridState(
    lazyGridState = lazyGridState,
    onMove = { from, to ->
      // Calculate adjusted index for proper reordering
      val adjustedToIndex = calculateAdjustedIndex(
        milestones = localVisibleMilestones,
        fromIndex = from.index,
        toIndex = to.index,
      )

      // Direct reordering with adjusted index
      localVisibleMilestones = localVisibleMilestones.toMutableList().apply {
        val item = removeAt(from.index)
        add(adjustedToIndex, item)
      }

      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

      // Call the reorder callback with the new order
      onMilestoneReordered(localVisibleMilestones)
    },
  )

  val minCellSize = 160.dp // Adjust as needed for design
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = minCellSize),
    state = lazyGridState,
    contentPadding = PaddingValues(MeTheme.spacing.sm),
    userScrollEnabled = false,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = 500.dp),
    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
  ) {
    // Visible milestones (reorderable)
    items(
      items = localVisibleMilestones,
      key = { getMilestoneKey(it, isVisible = true) },
      span = { milestone ->
        if (isGoalProgressMilestone(milestone)) {
          GridItemSpan(2)
        } else {
          GridItemSpan(1)
        }
      },
    ) { milestone ->
      ReorderableItem(
        state = reorderableState,
        key = getMilestoneKey(milestone, isVisible = true),
        enabled = inEditMode,
      ) { isDragging ->
        MilestoneItem(
          progress = progress,
          milestone = milestone,
          inEditMode = inEditMode,
          isDragging = isDragging,
          isVisible = true,
          onMilestoneMoved = onMilestoneMoved,
          reorderableScope = this,
        )
      }
    }
    // Hidden milestones (not reorderable)
    if (inEditMode) {
      items(
        items = hiddenMilestones,
        key = { stat -> getMilestoneKey(stat, isVisible = false) },
        span = { milestone ->
          if (isGoalProgressMilestone(milestone)) {
            GridItemSpan(2)
          } else {
            GridItemSpan(1)
          }
        },
      ) { milestone ->
        MilestoneItem(
          progress = progress,
          milestone = milestone,
          inEditMode = true,
          isVisible = false,
          onMilestoneMoved = onMilestoneMoved,
          reorderableScope = null,
        )
      }
    }
  }
}

/**
 * Calculates the adjusted target index for reordering based on grid layout and item spans.
 * This function ensures proper positioning when items have different spans (1 or 2 columns).
 *
 * @param milestones The list of milestones to reorder
 * @param fromIndex The source index of the item being moved
 * @param toIndex The raw target index from the reorderable library
 * @return The adjusted target index (Int) that accounts for grid spans and ensures proper visual positioning
 */
private fun calculateAdjustedIndex(
  milestones: List<Stat>,
  fromIndex: Int,
  toIndex: Int,
): Int {
  if (fromIndex == toIndex || fromIndex !in milestones.indices || toIndex !in milestones.indices) {
    return toIndex
  }

  // Precompute spans for all items
  val spans = milestones.map { if (isGoalProgressMilestone(it)) 2 else 1 }

  // Compute visual positions in a single pass
  val visualPositions = IntArray(milestones.size)
  var position = 0
  for (i in milestones.indices) {
    visualPositions[i] = position
    position += spans[i]
  }

  visualPositions[fromIndex]
  val toVisual = visualPositions[toIndex]

  // Calculate the target visual position based on movement direction
  val targetVisualPosition = if (fromIndex < toIndex) {
    toVisual + spans[toIndex] // Move after target item
  } else {
    toVisual // Move before target item
  }

  // Find the target index that matches the target visual position
  var accumulated = 0
  for (i in milestones.indices) {
    if (accumulated == targetVisualPosition) {
      return i.coerceAtMost(milestones.size - 1)
    }
    accumulated += spans[i]
  }

  // If we reach the end, place at the end
  return milestones.size - 1
}



