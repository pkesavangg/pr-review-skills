package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.domain.model.common.Progress
import com.greatergoods.meapp.features.common.helper.StatHelper
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable for the dashboard milestone section that displays weight progress and milestone stats.
 *
 * @param progress Progress data containing weight information
 * @param goal Goal data for weight targets
 * @param inEditMode Whether the dashboard is in edit mode
 * @param visibleKeys List of currently visible dashboard keys
 * @param onMilestonesChanged Callback when visible milestones are changed (for save functionality)
 * @param modifier Modifier for the composable
 */
@Composable
fun DashboardMilestone(
  progress: Progress,
  inEditMode: Boolean = false,
  visibleKeys: List<DashboardKey> = listOf(),
  onMilestonesChanged: (List<DashboardKey>) -> Unit = { },
  modifier: Modifier = Modifier
) {
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
  )
  val allMilestones = StatHelper.getMilestone(progress = progress, visibleKeys = null, filterNulls = false)
  val hiddenMilestones = allMilestones.filter { it !in visibleMilestones }

  val onMilestoneMoved = { fromVisible: Boolean, toVisible: Boolean, milestone: Stat ->
    val milestoneKey = milestone.key
    if (fromVisible && !toVisible) {
      val newKeys = localVisibleKeys.filterNot { it == milestoneKey }
      localVisibleKeys = newKeys
      onMilestonesChanged(newKeys)
    } else if (!fromVisible && toVisible) {
      val newKeys = localVisibleKeys + milestoneKey
      localVisibleKeys = newKeys
      onMilestonesChanged(newKeys)
    }
  }

  Column(modifier = modifier) {
    DashboardMilestoneGrid(
      visibleMilestones = visibleMilestones,
      hiddenMilestones = hiddenMilestones,
      inEditMode = inEditMode,
      onMilestoneMoved = onMilestoneMoved,
      progress = progress,
    )
  }

  Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
}
