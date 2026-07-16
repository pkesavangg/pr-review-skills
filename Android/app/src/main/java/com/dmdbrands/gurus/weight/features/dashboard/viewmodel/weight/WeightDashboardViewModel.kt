package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toGoal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeightDashboardViewModel @Inject constructor(
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val dashboardService: IDashboardService,
  private val goalService: IGoalService,
  private val healthConnectService: IHealthConnectService,
  private val entryReadService: IEntryReadService,
) : BaseDashboardViewModel<WeightDashboardState, BaseGraphIntent>(
  reducer = WeightDashboardReducer(),
), DefaultLifecycleObserver {

  companion object {
    private const val TAG = "WeightDashboardVM"
  }

  private var latestDailyData: List<PeriodBodyScaleSummary> = emptyList()
  private var latestMonthlyData: List<PeriodBodyScaleSummary> = emptyList()

  override fun handleIntent(intent: BaseGraphIntent) {
    if (intent is WeightDashboardIntent) {
      when (intent) {
        is WeightDashboardIntent.Refresh -> refresh()
        is WeightDashboardIntent.OnConnectScale -> navigateTo(AppRoute.AccountSettings.MyDevices)
        is WeightDashboardIntent.ResetDashboard -> showResetDashboardAlert()
        is WeightDashboardIntent.UpdateVisibleKeys -> updateVisibleKeys(intent.keys, intent.dashboardType)
        is WeightDashboardIntent.NavigateToGoal -> navigateTo(AppRoute.AccountSettings.Goal)
        is WeightDashboardIntent.OpenMetricInfo ->
          navigateTo(AppRoute.Dashboard.MetricInfo(intent.info, intent.key, intent.source))
        is WeightDashboardIntent.SetSelectedStat -> {
          viewModelScope.launch { dashboardService.setSelectedKey(intent.stat?.key) }
        }
        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  override fun provideInitialState(): WeightDashboardState = WeightDashboardState()

  init {
    initLoadData()
    subscribeWeightUnit()
    subscribeWeightless()
    subscribeGoal()
  }

  override fun onDependenciesReady() {
    startGraphSubscriptions()
    subscribeMetrics()
    subscribeDashboardType()
    subscribeProgress()
    subscribeProgressUpdating()
    subscribeLatestWeight()
    subscribeIsEmpty()
    subscribeSelectedKey()
  }

  override fun onResume(owner: LifecycleOwner) {
    viewModelScope.launch {
      val isOutOfSync = healthConnectService.outOfSyncState.first()
      if (isOutOfSync) healthConnectService.healthConnectOutOfSync()
    }
  }

  // ── Graph subscriptions (direct, no adapter) ──

  private fun startGraphSubscriptions() {
    viewModelScope.launch {
      entryReadService.getDailyGraphData(ProductSelection.MyWeight)
        .map { (it as? GraphData.Weight)?.data ?: emptyList() }
        .collect { entries ->
          latestDailyData = entries
          val series = toWeightSeries(entries)
          updateSegmentRanges(
            entries = entries,
            segments = listOf(GraphSegment.WEEK, GraphSegment.MONTH),
            goalWeight = _state.value.goal?.goalWeight ?: 0.0,
            isWeightlessMode = _state.value.weightless?.isWeightlessOn == true,
          ) { data ->
            data.filterIsInstance<PeriodBodyScaleSummary>()
              .map { it.weight }
              .filter { it.isFinite() && it > 0.0 }
          }
          pushWeightProducer(_state.value.dailyProducer, series)
        }
    }
    viewModelScope.launch {
      entryReadService.getMonthlyGraphData(ProductSelection.MyWeight)
        .map { (it as? GraphData.Weight)?.data ?: emptyList() }
        .collect { entries ->
          latestMonthlyData = entries
          val series = toWeightSeries(entries)
          updateSegmentRanges(
            entries = entries,
            segments = listOf(GraphSegment.YEAR, GraphSegment.TOTAL),
            goalWeight = _state.value.goal?.goalWeight ?: 0.0,
            isWeightlessMode = _state.value.weightless?.isWeightlessOn == true,
          ) { data ->
            data.filterIsInstance<PeriodBodyScaleSummary>()
              .map { it.weight }
              .filter { it.isFinite() && it > 0.0 }
          }
          pushWeightProducer(_state.value.monthlyProducer, series)
        }
    }
  }

  private fun toWeightSeries(entries: List<PeriodBodyScaleSummary>): List<SeriesData> {
    val sorted = entries.sortedBy { it.getTimeStamp() }
    val pairs = sorted.mapNotNull { e ->
      val w = e.weight
      // Only plot a real (positive) weight. A 0.0 is finite but not a valid reading — plotting it
      // drew a spurious point at the zero baseline on an otherwise-empty graph (MOB-1537). This
      // matches the axis seed filter (> 0.0) so the point and the Y-range agree.
      if (w.isFinite() && w > 0.0) e.getTimeStamp() to w else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  /** Push weight primary + optional secondary metric line to producer. */
  private fun pushWeightProducer(
    producer: com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer,
    primarySeries: List<SeriesData>,
  ) {
    val secondaryMetricKey = (_state.value.secondaryKey as? DashboardKey.Metric)?.key
    val data = if (producer == _state.value.dailyProducer) latestDailyData else latestMonthlyData
    val secondaryGraphLine = secondaryMetricKey?.let { data.toGraphPoints(it) }

    viewModelScope.launch(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        // No 0.0 placeholder line when there's no data — an empty transaction yields an empty chart
        // model, and the empty state is shown by EmptyDashboardGraph (via isEmptyGraph). Pushing a
        // (0.0, 0.0) point previously drew a spurious baseline at zero. (mirrors pushSeriesToProducer)
        if (primarySeries.isNotEmpty()) {
          lineSeries {
            primarySeries.forEach { s -> series(x = s.xValues, y = s.yValues) }
          }
        }
        if (secondaryGraphLine != null && secondaryGraphLine.points.isNotEmpty()) {
          val pairs = secondaryGraphLine.points.mapNotNull { point ->
            val x = point.x.value as? Long
            val y = (point.y.value as? Number)?.toDouble()
            if (x != null && y != null && y.isFinite()) x to y else null
          }
          if (pairs.isNotEmpty()) {
            lineSeries {
              series(x = pairs.map { it.first }, y = pairs.map { it.second })
            }
          }
        }
      }
    }
  }

  private fun subscribeSelectedKey() {
    viewModelScope.launch {
      dashboardService.selectedKey.drop(1).collect { key ->
        handleIntent(WeightDashboardIntent.SetSecondaryKey(key))
        pushWeightProducer(_state.value.dailyProducer, toWeightSeries(latestDailyData))
        pushWeightProducer(_state.value.monthlyProducer, toWeightSeries(latestMonthlyData))
      }
    }
  }

  // ── Other subscriptions (unchanged) ──

  private fun refresh() {
    viewModelScope.launch {
      AppLog.d(TAG, "Dashboard refresh started")
      setRefreshing(true)
      entryService.syncOperations()
      dashboardService.refreshDashboard()
      accountService.refreshAccount()
      setRefreshing(false)
      AppLog.i(TAG, "Dashboard refresh completed")
    }
  }

  private fun initLoadData() {
    val activeAccount = accountService.activeAccount.value
    val dashboardType = if (activeAccount?.dashboardType == DashboardType.DASHBOARD_12_METRICS.value)
      DashboardType.DASHBOARD_12_METRICS else DashboardType.DASHBOARD_4_METRICS
    val metrics = dashboardService.visibleKeys.value
    handleIntent(WeightDashboardIntent.SetDashboardType(dashboardType))
    handleIntent(WeightDashboardIntent.SetVisibleKeys(metrics))
  }

  private fun subscribeWeightUnit() {
    viewModelScope.launch {
      accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().collect { weightUnit ->
        if (weightUnit != null) handleIntent(WeightDashboardIntent.SetWeightUnit(weightUnit))
      }
    }
  }

  private fun subscribeWeightless() {
    viewModelScope.launch {
      accountService.activeAccountFlow
        .map { it?.toWeightless() }
        .distinctUntilChanged()
        .collect { weightless ->
          handleIntent(WeightDashboardIntent.SetWeightless(weightless))
        }
    }
  }

  private fun subscribeGoal() {
    viewModelScope.launch {
      // goalService flow is used only as a change signal — the emitted value
      // is intentionally dropped. The authoritative goal data lives on the
      // Account entity, so we rebuild it via activeAccount.toGoal() INSIDE the
      // collect block. This ensures we read the latest account state after
      // goalService emits (account may have been updated by the same write that
      // triggered the goal flow).
      goalService.getCurrentGoal().collect { _ ->
        val currentAccount = accountService.activeAccount.value
        val rawGoal = currentAccount?.toGoal()
        // goalWeight is stored in decigrams (Int). Dividing by 10.0 gives
        // display-lb with 0.1 precision. This display-lb value is never written
        // back to storage — it only flows to the chart/header for rendering.
        // Goal edits go through GoalService which operates on the decigram form
        // directly.
        val displayGoal = rawGoal?.copy(
          goalWeight = rawGoal.goalWeight / 10.0,
          initialWeight = rawGoal.initialWeight / 10.0,
        )
        handleIntent(WeightDashboardIntent.SetGoal(displayGoal))
      }
    }
  }

  private fun subscribeMetrics() {
    viewModelScope.launch {
      dashboardService.visibleKeys.drop(1).collect {
        handleIntent(WeightDashboardIntent.SetVisibleKeys(it))
      }
    }
  }

  private fun subscribeDashboardType() {
    viewModelScope.launch {
      accountService.activeAccountFlow.drop(1).collect { account ->
        if (account != null) {
          val dashboardType = if (account.dashboardType == DashboardType.DASHBOARD_12_METRICS.value)
            DashboardType.DASHBOARD_12_METRICS else DashboardType.DASHBOARD_4_METRICS
          handleIntent(WeightDashboardIntent.SetDashboardType(dashboardType))
        }
      }
    }
  }

  private fun subscribeProgress() {
    viewModelScope.launch {
      entryReadService.weightProgress().collect { handleIntent(WeightDashboardIntent.SetProgress(it)) }
    }
  }

  private fun subscribeProgressUpdating() {
    viewModelScope.launch {
      entryService.isUpdating.collect { handleIntent(WeightDashboardIntent.SetProgressUpdating(it)) }
    }
  }

  private fun subscribeLatestWeight() {
    viewModelScope.launch {
      entryReadService.latestEntry().collect { latestEntry ->
        val latestWeight = when (latestEntry) {
          is ScaleEntry -> latestEntry.scale.scaleEntry.weight
          else -> null
        }
        handleIntent(WeightDashboardIntent.SetLatestWeight(latestWeight))
      }
    }
  }

  private fun subscribeIsEmpty() {
    viewModelScope.launch {
      entryReadService.isWeightEmpty().collect { handleIntent(WeightDashboardIntent.SetIsEmpty(it)) }
    }
  }

  private fun showResetDashboardAlert() {
    val string = DashboardString.ResetDialog
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = string.Title,
        message = string.Message,
        confirmText = string.ConfirmText,
        cancelText = string.CancelText,
        onConfirm = { viewModelScope.launch { resetDashboard() } },
      ),
    )
  }

  private fun resetDashboard() {
    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(message = DashboardString.Loader.Save)
        dashboardService.resetVisibleKeys(dashboardType = state.value.dashboardType)
        handleIntent(WeightDashboardIntent.SetSelectedStat(null))
        // Signal the UI to exit edit mode and scroll back to the top, matching
        // iOS post-reset behavior (MOB-445).
        handleIntent(WeightDashboardIntent.ResetComplete)
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to reset dashboard", e)
      } finally {
        delay(300)
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun updateVisibleKeys(keys: List<DashboardKey>, dashboardType: DashboardType) {
    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(message = DashboardString.Loader.Save)
        dashboardService.updateVisibleKeys(keys = keys, dashboardType = dashboardType)
        handleIntent(WeightDashboardIntent.SetSelectedStat(null))
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to update visible keys", e)
      } finally {
        delay(300)
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun navigateTo(route: AppRoute) {
    viewModelScope.launch { navigationService.navigateTo(route) }
  }
}
