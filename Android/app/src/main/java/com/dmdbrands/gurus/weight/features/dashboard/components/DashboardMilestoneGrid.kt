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
  val currentDeviceType = getDeviceType()
  val spanCount = if (currentDeviceType == DeviceType.Tablet) 3 else 2

  var localVisibleMilestones by remember(visibleMilestones) {
    mutableStateOf(visibleMilestones.reorderGridComprehensive(spanCount, isAfterRemoval = false))
  }
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

  // Handle milestone movement with goal repositioning
  val handleMilestoneMoved = { isAdded: Boolean, milestone: Stat ->
    onMilestoneMoved(isAdded, milestone)

    // If a milestone was removed (hidden), reposition the goal card
    if (!isAdded && !isGoalProgressMilestone(milestone)) {
      // Determine where the tile was before removal so we know if it was from the first row
      val removedIndex = localVisibleMilestones.indexOf(milestone)
      val wasFromFirstRow = removedIndex in 0 until spanCount

      // Update local state to reflect the removal
      localVisibleMilestones = localVisibleMilestones.filter { it != milestone }

      // Reposition goal card after removal using comprehensive logic, aware of first-row removals
      localVisibleMilestones = localVisibleMilestones.reorderGridComprehensive(
        spanCount = spanCount,
        isAfterRemoval = true,
        removedFromFirstRow = wasFromFirstRow,
      )

      // Notify parent of the reordered state
      onMilestoneReordered(localVisibleMilestones)
    }
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
        enabled = inEditMode && !isGoalProgressMilestone(milestone),
      ) { isDragging ->
        MilestoneItem(
          progress = progress,
          milestone = milestone,
          inEditMode = inEditMode,
          isDragging = isDragging,
          isFromSetup = isFromSetup,
          isVisible = true,
          onMilestoneMoved = handleMilestoneMoved,
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
          onMilestoneMoved = handleMilestoneMoved,
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

/**
 * Repositions the goal card to snap above the last odd visible tile after a tile is removed.
 * This handles cases where removing a tile near the goal card should cause it to move up.
 *
 * Examples:
 * - If we have [1,2,3,4,5,6,goal] and remove 6, goal should move to [1,2,3,4,5,goal]
 * - If we have [1,2,3,4,5,6,7,goal] and remove 4, goal should move to [1,2,3,goal,5,6,7]
 *
 * @param spanCount Number of columns in the grid (2 for mobile, 3 for tablet)
 * @return New list with goal card repositioned appropriately
 */
fun List<Stat>.repositionGoalAfterRemoval(spanCount: Int): List<Stat> {
  if (isEmpty()) return this

  val goalIdx = indexOfFirst { isGoalProgressMilestone(it) }
  if (goalIdx == -1) return this

  // Get all non-goal milestones (regular tiles)
  val regularMilestones = filterNot { isGoalProgressMilestone(it) }
  if (regularMilestones.isEmpty()) return this

  // Calculate how many tiles are in the last incomplete row
  val totalRegularTiles = regularMilestones.size
  val tilesPerRow = spanCount
  val completeRows = totalRegularTiles / tilesPerRow
  val tilesInLastRow = totalRegularTiles % tilesPerRow

  // Determine where to place the goal card
  val insertPosition = when {
    tilesInLastRow == 0 -> totalRegularTiles
    tilesInLastRow == 1 -> completeRows * tilesPerRow
    else -> completeRows * tilesPerRow
  }

  // Create new list with goal repositioned
  val result = mutableListOf<Stat>()
  var regularIndex = 0

  for (i in indices) {
    if (isGoalProgressMilestone(this[i])) {
      // Skip goal for now, we'll insert it at the calculated position
      continue
    } else {
      result.add(this[i])
      regularIndex++

      // Insert goal after the calculated number of regular tiles
      if (regularIndex == insertPosition && goalIdx != i) {
        result.add(this[goalIdx])
      }
    }
  }

  // If goal wasn't inserted yet (shouldn't happen), add it at the end
  if (!result.any { isGoalProgressMilestone(it) }) {
    result.add(this[goalIdx])
  }

  return result
}

/**
 * Comprehensive grid reordering that handles both initial positioning and repositioning after removal.
 * This function ensures the goal card is always positioned optimally based on the current tile layout.
 *
 * Handles the following scenarios:
 * 1. Initial positioning: Goal card starts on a new row if it wouldn't fit in current row
 * 2. After removal: Goal card snaps above the last odd visible tile
 *
 * Examples for mobile (spanCount=2):
 * - [1,2,3,4,5,6,goal] -> remove 6 -> [1,2,3,4,5,goal]
 * - [1,2,3,4,5,6,7,goal] -> remove 4 -> [1,2,3,goal,5,6,7]
 *
 * @param spanCount Number of columns in the grid (2 for mobile, 3 for tablet)
 * @param isAfterRemoval Whether this is being called after a tile removal (affects positioning logic)
 * @param removedFromFirstRow Whether the removed tile came from the first row (fix for Case 1)
 * @return New list with optimal goal card positioning
 */
fun List<Stat>.reorderGridComprehensive(
  spanCount: Int,
  isAfterRemoval: Boolean = false,
  removedFromFirstRow: Boolean = false
): List<Stat> {
  if (isEmpty()) return this

  val goalIdx = indexOfFirst { isGoalProgressMilestone(it) }
  if (goalIdx == -1) return this

  // Get all non-goal milestones (regular tiles)
  val regularMilestones = filterNot { isGoalProgressMilestone(it) }
  if (regularMilestones.isEmpty()) return this

  // Calculate optimal position for goal card
  val totalRegularTiles = regularMilestones.size
  val tilesPerRow = spanCount
  val completeRows = totalRegularTiles / tilesPerRow
  val tilesInLastRow = totalRegularTiles % tilesPerRow

  val insertPosition = when {
    // Case 1: removed from first row → goal to the very beginning
    isAfterRemoval && removedFromFirstRow -> 0

    // After-removal general behavior (Cases 2 & 3 unchanged)
    isAfterRemoval -> when {
      // If last row has 0 tiles (shouldn't happen), goal goes at end
      tilesInLastRow == 0 -> totalRegularTiles
      // If last row has 1 tile, or 2+ tiles — both snap above the last odd block
      else -> completeRows * tilesPerRow
    }

    // Initial positioning (unchanged)
    else -> {
      val usedBefore = (0 until goalIdx).sumOf {
        if (isGoalProgressMilestone(this[it])) spanCount else 1
      } % spanCount
      val remaining = spanCount - usedBefore

      if (usedBefore == 0 || spanCount <= remaining) {
        goalIdx // Keep current position
      } else {
        // Move to next available position
        var moveBy = 0
        var filled = 0
        while (goalIdx + 1 + moveBy <= lastIndex && filled < remaining) {
          filled += if (isGoalProgressMilestone(this[goalIdx + 1 + moveBy])) spanCount else 1
          moveBy++
        }
        (goalIdx + moveBy).coerceAtMost(lastIndex)
      }
    }
  }

  // Create new list with goal repositioned
  val result = mutableListOf<Stat>()
  var regularIndex = 0

  for (i in indices) {
    if (isGoalProgressMilestone(this[i])) {
      // Skip goal for now, we'll insert it at the calculated position
      continue
    } else {
      result.add(this[i])
    }
  }

  // If goal wasn't inserted yet, add it at the end
  if (!result.any { isGoalProgressMilestone(it) }) {
    result.add(insertPosition, this[goalIdx])
  }

  return result
}



