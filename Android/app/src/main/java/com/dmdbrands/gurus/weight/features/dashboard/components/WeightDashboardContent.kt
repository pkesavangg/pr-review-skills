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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromPeriodSummaries
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.getSourceFromSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Weight-specific below-chart content. Pure MVI — dispatches intents only.
 * Manages its own edit mode state locally.
 */
@Composable
fun WeightDashboardContent(
  state: WeightDashboardState,
  activeSegmentState: SegmentState,
  handleIntent: (WeightDashboardIntent) -> Unit,
) {
  var inEditMode by remember { mutableStateOf(false) }
  var currentVisibleMetrics by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Metric })
  }
  var currentVisibleMilestones by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Milestone })
  }

  // After a RESET DASHBOARD completes the VM bumps resetSignal. Exit edit mode
  // so the control panel reverts to the view-mode cluster, matching iOS
  // (MOB-445). Guarded so the initial composition (resetSignal == 0) is a no-op.
  LaunchedEffect(state.resetSignal) {
    if (state.resetSignal > 0) inEditMode = false
  }

  if (state.isEmpty) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      EmptyMetric(onConnectScaleClick = { handleIntent(WeightDashboardIntent.OnConnectScale) })
    }
  } else {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .clickable(onClick = {
          if (inEditMode) {
            currentVisibleMetrics = state.visibleKeys.filterIsInstance<DashboardKey.Metric>()
            currentVisibleMilestones = state.visibleKeys.filterIsInstance<DashboardKey.Milestone>()
            inEditMode = false
          }
        }),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      DashboardMetrics(
        metricData = activeSegmentState.target.filterIsInstance<PeriodBodyScaleSummary>(),
        inEditMode = inEditMode,
        visibleKeys = currentVisibleMetrics,
        selectedStat = state.selectedStat,
        dashboardType = state.dashboardType,
        onMetricClick = { handleIntent(WeightDashboardIntent.SetSelectedStat(it)) },
        onLongClick = {
          if (!inEditMode) {
            handleIntent(WeightDashboardIntent.SetSelectedStat(null))
            inEditMode = true
          }
        },
        onMetricsChanged = { currentVisibleMetrics = it },
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
        onMilestonesChanged = { currentVisibleMilestones = it },
        onLongClick = { _, _ ->
          if (!inEditMode) {
            handleIntent(WeightDashboardIntent.SetSelectedStat(null))
            inEditMode = true
          }
        },
        onNavigateToGoal = { handleIntent(WeightDashboardIntent.NavigateToGoal) },
      )
      if ((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      }
      DashboardControlPanel(
        inEditMode = inEditMode,
        hasGoal = state.progress.goal?.account != null && state.progress.goal.account.goalType != null,
        onResetClick = {
          handleIntent(WeightDashboardIntent.SetSelectedStat(null))
          handleIntent(WeightDashboardIntent.ResetDashboard)
        },
        onEditClick = { editMode ->
          if (editMode && !inEditMode) {
            handleIntent(WeightDashboardIntent.SetSelectedStat(null))
          } else if (!editMode && inEditMode) {
            handleIntent(WeightDashboardIntent.UpdateVisibleKeys(currentVisibleMetrics + currentVisibleMilestones, state.dashboardType))
          }
          inEditMode = editMode
        },
        onUpdateGoalClick = { handleIntent(WeightDashboardIntent.NavigateToGoal) },
        onMetricInfoClick = {
          val isSingleEntry = state.markerIndex != null
          val rangeText = (activeSegmentState.visibleMin ?: activeSegmentState.chartMinX?.toLong())?.let { min ->
            (activeSegmentState.visibleMax ?: activeSegmentState.chartMaxX?.toLong())?.let { max ->
              GraphUtil.formatDateRange(min, max, state.selectedSegment)
            }
          }
          val info = fromPeriodSummaries(
            periodBodyScaleSummaries = activeSegmentState.target.filterIsInstance<PeriodBodyScaleSummary>(),
            isSingleEntry = isSingleEntry,
            rangeText = rangeText,
          )
          val key = (state.selectedStat?.key as? DashboardKey.Metric)?.key ?: MetricKey.WEIGHT
          handleIntent(
            WeightDashboardIntent.OpenMetricInfo(
              info = info,
              key = key,
              source = getSourceFromSegment(state.selectedSegment),
            ),
          )
        },
      )
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
