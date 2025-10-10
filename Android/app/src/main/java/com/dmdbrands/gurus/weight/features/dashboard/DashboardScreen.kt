package com.dmdbrands.gurus.weight.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromPeriodSummary
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.getSourceFromSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardControlPanel
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardMetrics
import com.dmdbrands.gurus.weight.features.dashboard.components.DashboardMilestone
import com.dmdbrands.gurus.weight.features.dashboard.components.EmptyMetric
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
  val viewmodel: DashboardViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsState()
  val context = LocalContext.current
  val activity = context as? AppCompatActivity

  val scope = rememberCoroutineScope()
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewmodel.onResume(lifecycleOwner)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  BackHandler {
    viewmodel.dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = "Exit Dashboard",
        message = "Are you sure you want to exit the dashboard?",
        onConfirm = {
          scope.launch {
            activity?.finishAffinity()
          }
        },
      ),
    )
  }
  PullToRefreshBox(
    isRefreshing = state.isRefreshing,
    onRefresh = {
      viewmodel.handleIntent(DashboardIntent.Refresh)
    },
  ) {
    DashboardScreenContent(state, viewmodel::handleIntent)
  }
}

@Composable
private fun DashboardScreenContent(state: DashboardState, handleIntent: (DashboardIntent) -> Unit) {
  val scrollState = rememberScrollState()
  val scope = rememberCoroutineScope()
  val navBackStack = LocalNavBackStack.current
  var inEditMode by remember { mutableStateOf(false) }
  var currentVisibleMetrics by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Metric })
  }
  var currentVisibleMilestones by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Milestone })
  }

  val selectedSegment = state.selectedSegment
  val selectedStat = state.selectedStat

  AppScaffold(title = null) {
    Column(modifier = if (state.latestWeight == null) Modifier.fillMaxHeight() else Modifier.verticalScroll(scrollState)) {

      GraphPagerView(
        state = state,
        selectedStat = selectedStat,
        onSegmentChange = {
          handleIntent(DashboardIntent.SetSelectedSegment(it))
        },
        onSelected = {
          handleIntent(DashboardIntent.SetData(it))
        },
        onPagerStateChange = { pagerState ->
          handleIntent(DashboardIntent.SetPagerState(pagerState))
        },
        onScrollTargetChange = { scrollTarget ->
          handleIntent(DashboardIntent.SetScrollTarget(scrollTarget))
        },
      )


      if (state.latestWeight == null) {
        Spacer(modifier = Modifier.height(MeTheme.spacing.x4l))
        EmptyMetric(
          onConnectScaleClick = {
            handleIntent(DashboardIntent.OnConnectScale)
          },
        )
      } else {
        DashboardMetrics(
          metricData = state.data,
          inEditMode = inEditMode,
          visibleKeys = currentVisibleMetrics,
          selectedStat = selectedStat,
          dashboardType = state.dashboardType,
          onMetricClick = { stat ->
            handleIntent(DashboardIntent.SetSelectedStat(stat))
          },
          onMetricsChanged = { visibleMetrics ->
            currentVisibleMetrics = visibleMetrics
          },
        )
        if((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
          HorizontalDivider(
            color = MeTheme.colorScheme.utility,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.lg),
          )
        }

        DashboardMilestone(
          progress = state.progress,
          latestWeight = state.latestWeight,
          inEditMode = inEditMode,
          visibleKeys = currentVisibleMilestones,
          onMilestonesChanged = { visibleMilestones ->
            currentVisibleMilestones = visibleMilestones
          },
        )
        if((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
          Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        }
        DashboardControlPanel(
          inEditMode = inEditMode,
          onResetClick = {
            handleIntent(
              DashboardIntent.ResetDashboard(
                onConfirm = {
                  inEditMode = false
                },
              ),
            )
          },
          onEditClick = { editMode ->
            if (!editMode && inEditMode) {
              // Save dashboard metrics and milestones when exiting edit mode
              val allVisibleKeys =
                currentVisibleMetrics + currentVisibleMilestones
              handleIntent(DashboardIntent.UpdateVisibleKeys(allVisibleKeys, state.dashboardType))
            }
            inEditMode = editMode
          },
          onUpdateGoalClick = {
            scope.launch {
              navBackStack.addRoute(AppRoute.AccountSettings.Goal)
            }
          },
          onMetricInfoClick = {
            scope.launch {
              navBackStack.addRoute(
                route = AppRoute.Dashboard.MetricInfo(
                  info = fromPeriodSummary(state.data.first()),
                  key = (selectedStat?.key as DashboardKey.Metric?)?.key ?: MetricKey.WEIGHT,
                  source = getSourceFromSegment(
                    selectedSegment,
                  ),
                ),
              )
            }
          },
        )
      }
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
