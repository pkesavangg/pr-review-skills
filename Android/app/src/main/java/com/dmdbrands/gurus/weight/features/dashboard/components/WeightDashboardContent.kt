package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * Weight-specific below-chart content: metric cards + goal progress + milestones + control panel.
 * Extracted from DashboardScreen for product-specific rendering.
 */
@Composable
fun WeightDashboardContent(
  state: DashboardState,
  inEditMode: Boolean,
  currentVisibleMetrics: List<DashboardKey>,
  currentVisibleMilestones: List<DashboardKey>,
  onEditModeChange: (Boolean) -> Unit,
  onMetricsChanged: (List<DashboardKey>) -> Unit,
  onMilestonesChanged: (List<DashboardKey>) -> Unit,
  onMetricClick: (com.dmdbrands.gurus.weight.features.common.model.Stat?) -> Unit,
  onLongClick: () -> Unit,
  onResetClick: () -> Unit,
  onEditClick: (Boolean) -> Unit,
  onUpdateGoalClick: () -> Unit,
  onMetricInfoClick: () -> Unit,
  onConnectScaleClick: () -> Unit,
) {
  if (state.isEmpty) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      EmptyMetric(onConnectScaleClick = onConnectScaleClick)
    }
  } else {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .clickable(onClick = {
          if (inEditMode) {
            onEditModeChange(false)
          }
        }),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      DashboardMetrics(
        metricData = state.data,
        inEditMode = inEditMode,
        visibleKeys = currentVisibleMetrics,
        selectedStat = state.selectedStat,
        dashboardType = state.dashboardType,
        onMetricClick = onMetricClick,
        onLongClick = { onLongClick() },
        onMetricsChanged = onMetricsChanged,
      )
      if ((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
        HorizontalDivider(
          color = MeTheme.colorScheme.utility,
          modifier = Modifier.padding(horizontal = MeTheme.spacing.lg),
        )
      }

      DashboardMilestone(
        progress = state.progress,
        isProgressUpdating = state.isProgressUpdating,
        latestWeight = state.latestWeight,
        inEditMode = inEditMode,
        hasVisibleMetrics = currentVisibleMetrics.isNotEmpty(),
        visibleKeys = currentVisibleMilestones,
        onMilestonesChanged = onMilestonesChanged,
        onLongClick = { _, _ -> onLongClick() },
        onNavigateToGoal = onUpdateGoalClick,
      )
      if ((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      }
      DashboardControlPanel(
        inEditMode = inEditMode,
        hasGoal = state.progress.goal?.account != null && state.progress.goal.account.goalType != null,
        onResetClick = onResetClick,
        onEditClick = onEditClick,
        onUpdateGoalClick = onUpdateGoalClick,
        onMetricInfoClick = onMetricInfoClick,
      )
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
