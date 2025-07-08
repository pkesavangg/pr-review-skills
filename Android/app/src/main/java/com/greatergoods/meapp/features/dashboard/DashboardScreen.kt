package com.greatergoods.meapp.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.dashboard.components.DashboardControlPanel
import com.greatergoods.meapp.features.dashboard.components.DashboardMetrics
import com.greatergoods.meapp.features.dashboard.components.DashboardMilestone
import com.greatergoods.meapp.features.dashboard.components.HistoryGraph
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardIntent
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen() {
  val viewmodel: DashboardViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsState()
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()

  BackHandler {
    viewmodel.dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = "Exit Dashboard",
        message = "Are you sure you want to exit the dashboard?",
        onConfirm = {
          scope.launch {
            activity?.finish()
          }
        },
      ),
    )
  }
  DashboardScreenContent(state, viewmodel::handleIntent)
}

@Composable
private fun DashboardScreenContent(state: DashboardState, handleIntent: (DashboardIntent) -> Unit) {
  val scrollState = rememberScrollState()
  var metricData: List<PeriodBodyScaleSummary> by remember {
    mutableStateOf(listOf())
  }
  var selectedStat: Stat? by remember {
    mutableStateOf(null)
  }
  var inEditMode by remember {
    mutableStateOf(false)
  }
  var currentVisibleMetrics by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Metric })
  }

  var currentVisibleMilestones by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Milestone })
  }

  AppScaffold(title = null) {
    Column(modifier = Modifier.verticalScroll(scrollState)) {
      HistoryGraph(state, selectedStat) {
        metricData = it
      }
      DashboardMetrics(
        metricData = metricData,
        inEditMode = inEditMode,
        visibleKeys = currentVisibleMetrics,
        selectedStat = selectedStat,
        onMetricClick = { stat ->
          selectedStat = stat
        },
        onMetricsChanged = { visibleMetrics ->
          currentVisibleMetrics = visibleMetrics
        },
      )
      HorizontalDivider(
        color = MeTheme.colorScheme.utility,
        modifier = Modifier.padding(horizontal = MeTheme.spacing.lg),
      )
      DashboardMilestone(
        progress = state.progress,
        inEditMode = inEditMode,
        visibleKeys = currentVisibleMilestones,
        onMilestonesChanged = { visibleMilestones ->
          currentVisibleMilestones = visibleMilestones
        },
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      DashboardControlPanel(
        inEditMode = inEditMode,
        onEditClick = { editMode ->
          if (!editMode && inEditMode) {
            // Save dashboard metrics and milestones when exiting edit mode
            val allVisibleKeys =
              currentVisibleMetrics + currentVisibleMilestones
            handleIntent(DashboardIntent.UpdateVisibleKeys(allVisibleKeys))
          }
          inEditMode = editMode
        },
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    }
  }
}

@PreviewTheme
@Composable
private fun DashboardPreview() {
  MeAppTheme {
    DashboardScreenContent(
      state = DashboardState(),
      handleIntent = {},
    )
  }
}
