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
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
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
  isFromSetup: Boolean,
  onMilestoneMoved: (isAdded: Boolean, milestone: Stat) -> Unit,
  onMilestoneReordered: (List<Stat>) -> Unit,
) {
  var localVisibleMilestones by remember(visibleMilestones) { mutableStateOf(visibleMilestones) }
  val hapticFeedback = LocalHapticFeedback.current
  val lazyGridState = rememberLazyGridState()

  val reorderableState = rememberReorderableLazyGridState(
    lazyGridState = lazyGridState,
    onMove = { from, to ->
      if (!isGoalProgressMilestone(localVisibleMilestones[to.index])) {
        // Direct reordering with adjusted index, then normalize layout for span-2 item
        localVisibleMilestones = localVisibleMilestones.toMutableList().apply {
          val item = removeAt(from.index)
          add(to.index, item)
        }

        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

        // Call the reorder callback with the new order
        onMilestoneReordered(localVisibleMilestones)
      }
    },
  )
  val currentDeviceType = getDeviceType()
  val spanCount = if (currentDeviceType == DeviceType.Tablet) {
    3
  } else {
    2
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(spanCount),
    state = lazyGridState,
    contentPadding = PaddingValues(vertical = MeTheme.spacing.sm),
    userScrollEnabled = false,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm)
      .heightIn(max = 800.dp),
    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.md),
  ) {
    // Visible milestones (reorderable)
    items(
      items = localVisibleMilestones,
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
        enabled = inEditMode,
      ) { isDragging ->
        MilestoneItem(
          progress = progress,
          milestone = milestone,
          inEditMode = inEditMode,
          isDragging = isDragging,
          isFromSetup = isFromSetup,
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
            GridItemSpan(spanCount)
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
          isFromSetup = isFromSetup,
          onMilestoneMoved = onMilestoneMoved,
          reorderableScope = null,
        )
      }
    }
  }
}

/**
 * Reorders so the first goal milestone starts on a new row if it wouldn't fit
 * in the remaining columns of its current row. Works for any spanCount and any
 * per-item span (read from Stat.span).
 */
fun List<Stat>.reorderGrid(spanCount: Int): List<Stat> {
  if (isEmpty()) return this

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



