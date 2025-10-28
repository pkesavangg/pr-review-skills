package com.dmdbrands.gurus.weight.features.dashboard

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric.Companion.fromPeriodSummaries
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

  rememberCoroutineScope()
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


  DashboardScreenContent(
    state,
    showDialog = viewmodel.dialogQueueService::showDialog,
    handleIntent = viewmodel::handleIntent,
  )
}

@Composable
private fun DashboardScreenContent(
  state: DashboardState,
  showDialog: (DialogModel) -> Unit,
  handleIntent: (DashboardIntent) -> Unit
) {
  val scrollState = rememberScrollState()
  rememberCoroutineScope()
  val navBackStack = LocalNavBackStack.current
  var inEditMode by remember { mutableStateOf(false) }
  var currentVisibleMetrics by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Metric })
  }
  var isSingleEntry by remember {
    mutableStateOf(false)
  }

  var rangeText: String? by remember {
    mutableStateOf(null)
  }
  var currentVisibleMilestones by remember(state.visibleKeys) {
    mutableStateOf(state.visibleKeys.filter { it is DashboardKey.Milestone })
  }

  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val activity = context as? AppCompatActivity
  BackHandler {
    if (!inEditMode && activity != null) {
      showDialog(
        DialogModel.Confirm(
          title = "Exit Dashboard",
          message = "Are you sure you want to exit the dashboard?",
          onConfirm = {
            scope.launch {
              activity.finishAffinity()
            }
          },
        ),
      )
    } else {
      scope.launch {
        scrollState.animateScrollTo(0)
      }
      if (inEditMode) {
        inEditMode = false
        currentVisibleMetrics = state.visibleKeys.filter { it is DashboardKey.Metric }
        currentVisibleMilestones = state.visibleKeys.filter { it is DashboardKey.Milestone }
      }
    }
  }

  AppScaffold(
    title = null,
    onRefresh = {
      handleIntent(DashboardIntent.Refresh)
    },
    isRefreshing = state.isRefreshing,
  ) {
    Column(
      modifier = if (state.isEmpty) Modifier.fillMaxHeight() else Modifier.verticalScroll(scrollState),
      verticalArrangement = if (state.isEmpty) Arrangement.SpaceBetween else Arrangement.Top,
    ) {

      GraphPagerView(
        state = state,
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
        onRangeChange = {
          rangeText = it
        },
        onMarkerIndexChange = {
          isSingleEntry = it != null
        },
      )


      if (state.isEmpty) {
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
          selectedStat = state.selectedStat,
          dashboardType = state.dashboardType,
          onMetricClick = { stat ->
            handleIntent(DashboardIntent.SetSelectedStat(stat))
          },
          onMetricsChanged = { visibleMetrics ->
            currentVisibleMetrics = visibleMetrics
          },
        )
        if ((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
          HorizontalDivider(
            color = MeTheme.colorScheme.utility,
            modifier = Modifier.padding(horizontal = MeTheme.spacing.lg),
          )
        }

        DashboardMilestone(
          progress = state.progress,
          latestWeight = state.latestWeight,
          inEditMode = inEditMode,
          hasVisibleMetrics = currentVisibleMetrics.isNotEmpty(),
          visibleKeys = currentVisibleMilestones,
          onMilestonesChanged = { visibleMilestones ->
            currentVisibleMilestones = visibleMilestones
          },
        )
        if ((!inEditMode && currentVisibleMilestones.isNotEmpty() && currentVisibleMetrics.isNotEmpty()) || inEditMode) {
          Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        }
        DashboardControlPanel(
          inEditMode = inEditMode,
          hasGoal = state.progress.goal?.account != null && state.progress.goal.account.goalType != null,
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
                  info = fromPeriodSummaries(state.data, isSingleEntry = isSingleEntry, rangeText = rangeText),
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
}

@PreviewTheme
@Composable
private fun DashboardPreview() {
  MeAppTheme {
    DashboardScreenContent(
      state = DashboardState(),
      showDialog = {},
      handleIntent = {},
    )
  }
}
