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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromPeriodSummaries
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.getSourceFromSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardViewModel
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * Weight-specific below-chart content. Manages its own edit mode state.
 * Reads directly from [WeightDashboardState] — no cross-VM sync.
 */
@Composable
fun WeightDashboardContent(
  state: WeightDashboardState,
  activeSegmentState: SegmentState,
  viewModel: WeightDashboardViewModel,
) {
  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()

  var inEditMode by remember { mutableStateOf(false) }
  var currentVisibleMetrics by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Metric })
  }
  var currentVisibleMilestones by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Milestone })
  }

  if (state.isEmpty) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      EmptyMetric(onConnectScaleClick = { viewModel.navigateTo(AppRoute.AccountSettings.AddEditScales) })
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
      // Metric cards read from active segment target (visible entries)
      DashboardMetrics(
        metricData = activeSegmentState.target,
        inEditMode = inEditMode,
        visibleKeys = currentVisibleMetrics,
        selectedStat = state.selectedStat,
        dashboardType = state.dashboardType,
        onMetricClick = { viewModel.setSelectedStat(it) },
        onLongClick = {
          if (!inEditMode) {
            viewModel.setSelectedStat(null)
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
            viewModel.setSelectedStat(null)
            inEditMode = true
          }
        },
        onNavigateToGoal = {
          scope.launch { navBackStack.addRoute(AppRoute.AccountSettings.Goal) }
        },
      )
      if ((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      }
      DashboardControlPanel(
        inEditMode = inEditMode,
        hasGoal = state.progress.goal?.account != null && state.progress.goal.account.goalType != null,
        onResetClick = { viewModel.showResetDashboardAlert { inEditMode = false } },
        onEditClick = { editMode ->
          if (editMode && !inEditMode) {
            viewModel.setSelectedStat(null)
          } else if (!editMode && inEditMode) {
            viewModel.updateVisibleKeys(currentVisibleMetrics + currentVisibleMilestones, state.dashboardType)
          }
          inEditMode = editMode
        },
        onUpdateGoalClick = {
          scope.launch { navBackStack.addRoute(AppRoute.AccountSettings.Goal) }
        },
        onMetricInfoClick = {
          val isSingleEntry = activeSegmentState.markerIndex != null
          val rangeText = activeSegmentState.minTarget?.let { min ->
            activeSegmentState.maxTarget?.let { max ->
              GraphUtil.formatDateRange(min, max, state.selectedSegment)
            }
          }
          scope.launch {
            navBackStack.addRoute(
              route = AppRoute.Dashboard.MetricInfo(
                info = fromPeriodSummaries(activeSegmentState.target, isSingleEntry = isSingleEntry, rangeText = rangeText),
                key = (state.selectedStat?.key as DashboardKey.Metric?)?.key ?: MetricKey.WEIGHT,
                source = getSourceFromSegment(state.selectedSegment),
              ),
            )
          }
        },
      )
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
