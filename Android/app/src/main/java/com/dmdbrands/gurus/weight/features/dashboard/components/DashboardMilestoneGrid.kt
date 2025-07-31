package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
      // Direct reordering with adjusted index
      localVisibleMilestones = localVisibleMilestones.toMutableList().apply {
        val item = removeAt(from.index)
        add(to.index, item)
      }

      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

      // Call the reorder callback with the new order
      onMilestoneReordered(localVisibleMilestones)
    },
  )

  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    state = lazyGridState,
    contentPadding = PaddingValues(vertical = MeTheme.spacing.sm),
    userScrollEnabled = false,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm)
      .heightIn(max = 800.dp),
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
 * Reorders the grid to ensure the goal progress milestone (span-2 item) is positioned
 * at an even cell index in the 2-column grid layout for optimal visual alignment.
 *
 * @return A new list with the goal progress milestone repositioned if necessary
 */
fun List<Stat>.reorderGrid(): List<Stat> {
  // Find the goal progress milestone that spans 2 columns
  val goalMilestoneIndex = indexOfFirst { isGoalProgressMilestone(it) }

  // Early exit if no goal milestone found or it's already at the end
  if (goalMilestoneIndex == -1 || goalMilestoneIndex == lastIndex) {
    return this
  }

  // If goal milestone is at odd index, move it to next position for proper alignment
  return if (goalMilestoneIndex % 2 != 0) {
    toMutableList().apply {
      val goalMilestone = removeAt(goalMilestoneIndex)
      add(goalMilestoneIndex + 1, goalMilestone)
    }
  } else {
    this
  }
}

