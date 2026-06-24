package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.theme.MeTheme
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Shimmer overlay shown on progress metric cards while progress is updating.
 * Uses theme loading color with animated alpha for a subtle loading effect.
 */
@Composable
private fun ProgressShimmerOverlay(
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition()
  val alpha by infiniteTransition.animateFloat(
    initialValue = 0.12f,
    targetValue = 0.28f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 800, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
  )
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(MeTheme.borderRadius.sm))
      .background(MeTheme.colorScheme.loading.copy(alpha = alpha)),
  )
}

/**
 * Single component for milestone items that handles both draggable and static cases.
 *
 * @param progress Progress snapshot containing weight information
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
  progress: WeightProgress,
  isProgressUpdating: Boolean = false,
  milestone: Stat,
  inEditMode: Boolean,
  isFromSetup: Boolean = false,
  isDragging: Boolean = false,
  isVisible: Boolean = true,
  latestWeight: Double? = null,
  modifier: Modifier = Modifier,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onNavigateToGoal: () -> Unit = {},
  onLongClick: (Stat?, WeightProgress?) -> Unit = { _, _ -> },
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
    val showProgressShimmer = isProgressUpdating && isProgressMetricMilestone(milestone) && isVisible
    Box(modifier = modifier) {
      AnimatedStatCard(
        stat = milestone,
        inEditMode = inEditMode,
        isDragging = isDragging,
        isFromSetup = isFromSetup,
        isSelected = null,
        canLongPress = true,
        isVisible = isVisible,
        modifier = Modifier.fillMaxSize(),
        onLongClick = {
          onLongClick(milestone, null)
        },
        onBadgeClick = {
          onMilestoneMoved(!isVisible, milestone)
        },
      )
      if (showProgressShimmer) {
        ProgressShimmerOverlay(modifier = Modifier.fillMaxSize())
      }
    }
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
