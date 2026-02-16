package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.common.model.Stat

/**
 * Single component for milestone items that handles both draggable and static cases.
 *
 * @param progress Progress data containing weight information
 * @param milestone The milestone stat
 * @param inEditMode Whether the dashboard is in edit mode
 * @param isFromSetup Whether the item is from setup flow
 * @param isDragging Whether the item is currently being dragged (only for draggable items)
 * @param isVisible Whether the milestone is currently visible
 * @param latestWeight Latest weight value for display
 * @param onMilestoneMoved Callback when milestone is moved
 * @param onNavigateToGoal Callback when navigating to goal screen
 * @param reorderableScope The reorderable scope for drag functionality (null for static items)
 */
@Composable
fun MilestoneItem(
  progress: Progress,
  milestone: Stat,
  inEditMode: Boolean,
  isFromSetup: Boolean = false,
  isDragging: Boolean = false,
  isVisible: Boolean = true,
  latestWeight: Double? = null,
  modifier: Modifier = Modifier,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onNavigateToGoal: () -> Unit = {},
  onLongClick: (Stat?, Progress?) -> Unit = { _, _ -> },
) {
  if (isGoalProgressMilestone(milestone)) {
    // Goal Progress Milestone Card
    GoalProgressMilestoneCard(
      progress = progress,
      inEditMode = inEditMode,
      isVisible = isVisible,
      latestWeight = latestWeight,
      modifier = modifier,
      isDragging = isDragging,
      onBadgeClick = {
        onMilestoneMoved(!isVisible, milestone)
      },
      onLongClick = {
        onLongClick(null, it)
      },
      onNavigateToGoal = onNavigateToGoal,
    )
  } else {
    AnimatedStatCard(
      stat = milestone,
      inEditMode = inEditMode,
      isDragging = isDragging,
      isFromSetup = isFromSetup,
      isSelected = null,
      canLongPress = true,
      isVisible = isVisible,
      modifier = modifier,
      onLongClick = {
        onLongClick(milestone, null)
      },
      onBadgeClick = {
        onMilestoneMoved(!isVisible, milestone)
      },
    )
  }
}

fun <T> Modifier.onLongPress(
  enabled: Boolean = true,
  key: T,
  onLongPress: (T) -> Unit,
  onClick: (T) -> Unit = {}
): Modifier {
  return this.combinedClickable(
    enabled = enabled,
    onLongClick = {
      onLongPress(key)
    },
    onClick = {
      onClick(key)
    },
  )
}
