package com.greatergoods.meapp.features.dashboard.components

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
import com.greatergoods.meapp.domain.model.common.Progress
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.theme.MeTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * Grid layout for displaying dashboard milestones.
 *
 * @param progress Progress data containing weight information
 * @param goal Goal data for weight targets
 * @param visibleMilestones List of currently visible milestone stats
 * @param hiddenMilestones List of hidden milestone stats
 * @param inEditMode Whether the dashboard is in edit mode
 * @param onMilestoneMoved Callback when a milestone is moved between visible and hidden states
 */
@Composable
fun DashboardMilestoneGrid(
  progress: Progress,
  visibleMilestones: List<Stat>,
  hiddenMilestones: List<Stat>,
  inEditMode: Boolean,
  onMilestoneMoved: (fromVisible: Boolean, toVisible: Boolean, milestone: Stat) -> Unit,
) {
  var localVisibleMilestones by remember(visibleMilestones) { mutableStateOf(visibleMilestones) }
  val hapticFeedback = LocalHapticFeedback.current
  val lazyGridState = rememberLazyGridState()
  val reorderableState = rememberReorderableLazyGridState(
    lazyGridState = lazyGridState,
    onMove = { from, to ->
      localVisibleMilestones = localVisibleMilestones.toMutableList().apply {
        add(to.index, removeAt(from.index))
      }
      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
      // Call onMilestonesChanged with the new order
      onMilestoneMoved(true, true, localVisibleMilestones[to.index])
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
