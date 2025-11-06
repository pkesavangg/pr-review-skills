package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Composable for the dashboard milestone section that displays weight progress and milestone stats.
 *
 * @param progress Progress data containing weight information
 * @param latestWeight Latest weight value for display
 * @param inEditMode Whether the dashboard is in edit mode
 * @param isFromSetup Whether the item is from setup flow
 * @param hasVisibleMetrics Whether there are visible metrics
 * @param visibleKeys List of currently visible dashboard keys
 * @param onMilestonesChanged Callback when visible milestones are changed (for save functionality)
 * @param onNavigateToGoal Callback when navigating to goal screen
 * @param modifier Modifier for the composable
 */
@Composable
fun DashboardMilestone(
  progress: Progress,
  latestWeight: Double? = null,
  inEditMode: Boolean = false,
  isFromSetup: Boolean = false,
  hasVisibleMetrics: Boolean = false,
  visibleKeys: List<DashboardKey> = listOf(),
  onMilestonesChanged: (List<DashboardKey>) -> Unit = { },
  onNavigateToGoal: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val currentDeviceType = getDeviceType()
  val spanCount = if (currentDeviceType == DeviceType.Tablet) {
    3
  } else {
    2
  }
  var localVisibleKeys by remember(visibleKeys) { mutableStateOf(visibleKeys) }

  val milestoneKeys = localVisibleKeys.mapNotNull { key ->
    when (key) {
      is DashboardKey.Milestone -> key.key
      is DashboardKey.Metric -> null
    }
  }
  val visibleMilestones = StatHelper.getMilestone(
    progress = progress,
    visibleKeys = milestoneKeys,
    filterNulls = false,
    unit = progress.unit,
  ).reorderGrid(
    spanCount = spanCount,
  )
  val allMilestones = StatHelper.getMilestone(progress = progress, visibleKeys = null, filterNulls = false)
  val hiddenMilestones = allMilestones.filter { it !in visibleMilestones }

  val onMilestoneMoved = { isAdded: Boolean, milestone: Stat ->
    val milestoneKey = milestone.key
    val newStats = if (!isAdded) {
      // Moving from visible to hidden
      visibleMilestones.filterNot { it.key == milestoneKey }
    } else {
      // Moving from hidden to visible
      visibleMilestones + milestone
    }.reorderGrid(
      spanCount = spanCount,
    )
    localVisibleKeys = newStats.map { stat ->
      stat.key
    }
    onMilestonesChanged(localVisibleKeys)
  }

  val onMilestoneReordered = { newOrder: List<Stat> ->
    val newVisibleKeys = newOrder.map { stat ->
      stat.key
    }
    localVisibleKeys = newVisibleKeys
    onMilestonesChanged(newVisibleKeys)
  }

  Column(modifier = modifier) {
    DashboardMilestoneGrid(
      visibleMilestones = visibleMilestones,
      hiddenMilestones = hiddenMilestones,
      inEditMode = inEditMode,
      isFromSetup = isFromSetup,
      hasVisibleMetrics = hasVisibleMetrics,
      onMilestoneMoved = onMilestoneMoved,
      onMilestoneReordered = onMilestoneReordered,
      onNavigateToGoal = if (inEditMode) {
        // Disable navigation to goal screen in edit mode
        {}
      } else {
        // Allow navigation to goal screen when not in edit mode
        onNavigateToGoal
      },
      progress = progress,
      latestWeight = latestWeight,
    )
  }

  Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
}
