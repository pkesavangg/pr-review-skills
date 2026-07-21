package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ReorderableItem
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ReorderableLazyGridState
import com.dmdbrands.gurus.weight.features.common.components.reorderable.rememberReorderableLazyGridState
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Grid layout for displaying dashboard milestones.
 *
 * @param progress Progress data containing weight information
 * @param visibleMilestones List of currently visible milestone stats
 * @param hiddenMilestones List of hidden milestone stats
 * @param hasVisibleMetrics Whether there are visible metrics
 * @param inEditMode Whether the dashboard is in edit mode
 * @param isFromSetup Whether the item is from setup flow
 * @param latestWeight Latest weight value for display
 * @param onMilestoneMoved Callback when a milestone is moved between visible and hidden states
 * @param onMilestoneReordered Callback when visible milestones are reordered
 * @param onNavigateToGoal Callback when navigating to goal screen
 */
@Composable
fun DashboardMilestoneGrid(
  progress: WeightProgress,
  visibleMilestones: List<Stat>,
  hiddenMilestones: List<Stat>,
  hasVisibleMetrics: Boolean = false,
  inEditMode: Boolean,
  isFromSetup: Boolean,
  isProgressUpdating: Boolean = false,
  latestWeight: Double? = null,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onMilestoneReordered: (List<Stat>) -> Unit,
  onLongClick: (Stat?, WeightProgress?) -> Unit = { _, _ -> },
  onNavigateToGoal: () -> Unit = {},
) {
  val currentDeviceType = getDeviceType()
  val spanCount = if (currentDeviceType == DeviceType.Tablet) 3 else 2
  var localVisibleMilestones by remember(visibleMilestones) {
    mutableStateOf(visibleMilestones.reorderGrid(spanCount))
  }
  val hapticFeedback = LocalHapticFeedback.current
  val lazyGridState = rememberLazyGridState()
  val reorderableState = rememberReorderableLazyGridState(
    lazyGridState = lazyGridState,
    onMove = { from, to ->
      if (!isGoalProgressMilestone(localVisibleMilestones[to.index])) {
        localVisibleMilestones = reorderMilestones(localVisibleMilestones, from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onMilestoneReordered(localVisibleMilestones)
      }
    },
  )
  // Handle milestone movement with goal repositioning
  val handleMilestoneMoved = { isAdded: Boolean, milestone: Stat ->
    onMilestoneMoved(isAdded, milestone)
    if (!isAdded && !isGoalProgressMilestone(milestone)) {
      val removedIndex = localVisibleMilestones.indexOf(milestone)
      removedIndex in 0 until spanCount
      localVisibleMilestones = localVisibleMilestones.filter { it != milestone }
      onMilestoneReordered(localVisibleMilestones)
    }
  }
  MilestoneGridBody(
    localVisibleMilestones = localVisibleMilestones,
    hiddenMilestones = hiddenMilestones,
    reorderableState = reorderableState,
    spanCount = spanCount,
    inEditMode = inEditMode,
    hasVisibleMetrics = hasVisibleMetrics,
    isFromSetup = isFromSetup,
    isProgressUpdating = isProgressUpdating,
    progress = progress,
    latestWeight = latestWeight,
    hapticFeedback = hapticFeedback,
    onMilestoneMoved = handleMilestoneMoved,
    onNavigateToGoal = onNavigateToGoal,
    onLongClick = onLongClick,
    lazyGridState = lazyGridState,
  )
}

/**
 * Computes the reordered milestone list for a drag move, keeping any goal card in
 * its correct position (extracted verbatim from the reorder onMove logic).
 */
private fun reorderMilestones(
  current: List<Stat>,
  fromIndex: Int,
  toIndex: Int,
): List<Stat> {
  val goalIndex = hasGoalCardBetweenIndices(current, fromIndex, toIndex)
  return if (goalIndex != null) {
    val tempLocalVisibleMileStone = current.toMutableList().apply {
      val item = removeAt(fromIndex)
      add(toIndex, item)
    }
    tempLocalVisibleMileStone.toMutableList().apply {
      val goalItemIndex =
        hasGoalCardBetweenIndices(tempLocalVisibleMileStone, -1, tempLocalVisibleMileStone.size)
      if (goalItemIndex != null) {
        val goalItem = removeAt(goalItemIndex)
        add(goalIndex, goalItem)
      }
    }
  } else {
    // 📦 Move item to 'to.index' and shift others accordingly
    current.toMutableList().apply {
      val item = removeAt(fromIndex)
      add(toIndex, item)
    }
  }
}

/**
 * The milestone [LazyVerticalGrid] itself: visible (reorderable) items plus the
 * hidden items shown while in edit mode.
 */
@Composable
private fun MilestoneGridBody(
  localVisibleMilestones: List<Stat>,
  hiddenMilestones: List<Stat>,
  reorderableState: ReorderableLazyGridState,
  spanCount: Int,
  inEditMode: Boolean,
  hasVisibleMetrics: Boolean,
  isFromSetup: Boolean,
  isProgressUpdating: Boolean,
  progress: WeightProgress,
  latestWeight: Double?,
  hapticFeedback: HapticFeedback,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onNavigateToGoal: () -> Unit,
  onLongClick: (Stat?, WeightProgress?) -> Unit,
  lazyGridState: LazyGridState,
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(spanCount),
    state = lazyGridState,
    contentPadding = if (inEditMode || hasVisibleMetrics) {
      PaddingValues(vertical = spacing.sm)
    } else PaddingValues(bottom = spacing.sm),
    userScrollEnabled = false,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = if (isFromSetup) 0.dp else spacing.sm)
      .heightIn(max = 800.dp),
    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    visibleMilestoneItems(
      milestones = localVisibleMilestones,
      spanCount = spanCount,
      reorderableState = reorderableState,
      inEditMode = inEditMode,
      progress = progress,
      isProgressUpdating = isProgressUpdating,
      isFromSetup = isFromSetup,
      hapticFeedback = hapticFeedback,
      onMilestoneMoved = onMilestoneMoved,
      onNavigateToGoal = onNavigateToGoal,
      onLongClick = onLongClick,
      latestWeight = latestWeight,
    )
    hiddenMilestoneItems(
      milestones = hiddenMilestones,
      spanCount = spanCount,
      inEditMode = inEditMode,
      progress = progress,
      isProgressUpdating = isProgressUpdating,
      isFromSetup = isFromSetup,
      onMilestoneMoved = onMilestoneMoved,
      onNavigateToGoal = onNavigateToGoal,
      latestWeight = latestWeight,
    )
  }
}

/**
 * Visible (reorderable) milestone items for the grid.
 */
private fun LazyGridScope.visibleMilestoneItems(
  milestones: List<Stat>,
  spanCount: Int,
  reorderableState: ReorderableLazyGridState,
  inEditMode: Boolean,
  progress: WeightProgress,
  isProgressUpdating: Boolean,
  isFromSetup: Boolean,
  hapticFeedback: HapticFeedback,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onNavigateToGoal: () -> Unit,
  onLongClick: (Stat?, WeightProgress?) -> Unit,
  latestWeight: Double?,
) {
  items(
    items = milestones,
    key = { getMilestoneKey(it, isVisible = true) },
    span = { milestone ->
      if (isGoalProgressMilestone(milestone)) {
        GridItemSpan(spanCount)
      } else {
        GridItemSpan(1)
      }
    },
  ) { milestone ->
    ReorderableItem(
      state = reorderableState,
      key = getMilestoneKey(milestone, isVisible = true),
      enabled = inEditMode && !isGoalProgressMilestone(milestone),
    ) { isDragging ->
      MilestoneItem(
        progress = progress,
        isProgressUpdating = isProgressUpdating,
        milestone = milestone,
        inEditMode = inEditMode,
        isDragging = isDragging,
        isFromSetup = isFromSetup,
        isVisible = true,
        modifier = Modifier.longPressDraggableHandle(
          enabled = inEditMode,
          onDragStarted = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
          },
          onDragStopped = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
          },
        ),
        onMilestoneMoved = onMilestoneMoved,
        onNavigateToGoal = onNavigateToGoal,
        onLongClick = onLongClick,
        latestWeight = latestWeight,
      )
    }
  }
}

/**
 * Hidden milestone items, shown only when in edit mode.
 */
private fun LazyGridScope.hiddenMilestoneItems(
  milestones: List<Stat>,
  spanCount: Int,
  inEditMode: Boolean,
  progress: WeightProgress,
  isProgressUpdating: Boolean,
  isFromSetup: Boolean,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onNavigateToGoal: () -> Unit,
  latestWeight: Double?,
) {
  if (!inEditMode) return
  items(
    items = milestones,
    key = { stat -> getMilestoneKey(stat, isVisible = false) },
    span = { milestone ->
      if (isGoalProgressMilestone(milestone)) {
        GridItemSpan(spanCount)
      } else {
        GridItemSpan(1)
      }
    },
  ) { milestone ->
    MilestoneItem(
      progress = progress,
      isProgressUpdating = isProgressUpdating,
      milestone = milestone,
      inEditMode = true,
      isVisible = false,
      isFromSetup = isFromSetup,
      onMilestoneMoved = onMilestoneMoved,
      onNavigateToGoal = onNavigateToGoal,
      latestWeight = latestWeight,
    )
  }
}

/**
 * Reorders so the first goal milestone starts on a new row if it wouldn't fit
 * in the remaining columns of its current row. Works for any spanCount and any
 * per-item span (read from Stat.span).
 */
fun List<Stat>.reorderGrid(spanCount: Int): List<Stat> {
  return this

  val idx = indexOfFirst { isGoalProgressMilestone(it) }
  if (idx == -1 || idx == lastIndex) return this

  // Your inline rule: milestone takes the whole row, others take 1
  fun Stat.itemSpan(): Int = when {
    isGoalProgressMilestone(this) -> spanCount
    else -> 1
  }.coerceIn(1, spanCount)

  // How many columns are already used in the current row
  val usedBefore = (0 until idx).sumOf { this[it].itemSpan() } % spanCount
  val targetSpan = this[idx].itemSpan()
  val remaining = spanCount - usedBefore

  // If it fits, keep order
  if (usedBefore == 0 || targetSpan <= remaining) return this

  // Otherwise, push it forward just enough items to close the row
  var moveBy = 0
  var filled = 0
  while (idx + 1 + moveBy <= lastIndex && filled < remaining) {
    filled += this[idx + 1 + moveBy].itemSpan()
    moveBy++
  }

  val insertPos = (idx + moveBy).coerceAtMost(lastIndex)

  return toMutableList().apply {
    val target = removeAt(idx)
    add(insertPos, target)
  }
}

/**
 * Checks if there's a goal progress milestone between two indices in the list.
 *
 * @param milestones List of milestone stats
 * @param fromIndex Starting index
 * @param toIndex Target index
 * @return true if there's a goal card between the indices, false otherwise
 */
private fun hasGoalCardBetweenIndices(
  milestones: List<Stat>,
  fromIndex: Int,
  toIndex: Int
): Int? {
  val startIndex = minOf(fromIndex, toIndex)
  val endIndex = maxOf(fromIndex, toIndex)

  // Check if there's a goal card in the range between startIndex and endIndex (exclusive)
  for (i in startIndex + 1 until endIndex) {
    if (i < milestones.size && isGoalProgressMilestone(milestones[i])) {
      return i
    }
  }

  return null
}




