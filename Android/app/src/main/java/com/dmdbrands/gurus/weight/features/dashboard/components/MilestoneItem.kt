package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.common.model.Stat
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * Single component for milestone items that handles both draggable and static cases.
 *
 * @param progress Progress data containing weight information
 * @param goal Goal data for weight targets
 * @param milestone The milestone stat
 * @param inEditMode Whether the dashboard is in edit mode
 * @param isDragging Whether the item is currently being dragged (only for draggable items)
 * @param isVisible Whether the milestone is currently visible
 * @param onMilestoneMoved Callback when milestone is moved
 * @param reorderableScope The reorderable scope for drag functionality (null for static items)
 */
@Composable
fun MilestoneItem(
  progress: Progress,
  milestone: Stat,
  inEditMode: Boolean,
  isDragging: Boolean = false,
  isVisible: Boolean = true,
  onMilestoneMoved: (fromVisible: Boolean, toVisible: Boolean, milestone: Stat) -> Unit,
  reorderableScope: ReorderableCollectionItemScope? = null
) {
  val hapticFeedback = LocalHapticFeedback.current
  val modifier = if (reorderableScope != null && inEditMode) {
    with(reorderableScope) {
      Modifier.draggableHandle(
        enabled = inEditMode,
        onDragStarted = {
          hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        },
        onDragStopped = {
          hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
        },

        )
    }
  } else {
    Modifier
  }
  if (isGoalProgressMilestone(milestone)) {
    // Goal Progress Milestone Card
    GoalProgressMilestoneCard(
      progress = progress,
      inEditMode = inEditMode,
      isVisible = isVisible,
      modifier = modifier,
      onBadgeClick = {
        val fromVisible = isVisible
        val toVisible = !isVisible
        onMilestoneMoved(fromVisible, toVisible, milestone)
      },
    )
  } else {
    AnimatedStatCard(
      stat = milestone,
      inEditMode = inEditMode,
      isDragging = isDragging,
      isSelected = if (isVisible) false else null,
      isVisible = isVisible,
      modifier = modifier,
      onBadgeClick = {
        val fromVisible = isVisible
        val toVisible = !isVisible
        onMilestoneMoved(fromVisible, toVisible, milestone)
      },
    )
  }
}
