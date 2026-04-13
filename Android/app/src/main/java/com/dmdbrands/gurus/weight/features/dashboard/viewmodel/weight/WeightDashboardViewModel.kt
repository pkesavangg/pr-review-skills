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
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
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
  private val historyService: IHistoryService,
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
        is WeightDashboardIntent.OnConnectScale -> navigateTo(AppRoute.AccountSettings.AddEditScales)
        is WeightDashboardIntent.ResetDashboard -> showResetDashboardAlert()
        is WeightDashboardIntent.UpdateVisibleKeys -> updateVisibleKeys(intent.keys, intent.dashboardType)
        is WeightDashboardIntent.NavigateToGoal -> navigateTo(AppRoute.AccountSettings.Goal)
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
      historyService.getDailyGraphData(ProductSelection.MyWeight)
        .map { (it as? GraphData.Weight)?.data ?: emptyList() }
        .collect { entries ->
          latestDailyData = entries
          val series = toWeightSeries(entries)
          updateSegmentRanges(entries, listOf(GraphSegment.WEEK, GraphSegment.MONTH)) { data ->
            data.filterIsInstance<PeriodBodyScaleSummary>()
              .map { it.weight }
              .filter { it.isFinite() && it > 0.0 }
          }
          pushWeightProducer(_state.value.dailyProducer, series)
        }
    }
    viewModelScope.launch {
      historyService.getMonthlyGraphData(ProductSelection.MyWeight)
        .map { (it as? GraphData.Weight)?.data ?: emptyList() }
        .collect { entries ->
          latestMonthlyData = entries
          val series = toWeightSeries(entries)
          updateSegmentRanges(entries, listOf(GraphSegment.YEAR, GraphSegment.TOTAL)) { data ->
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
      if (w.isFinite()) e.getTimeStamp() to w else null
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
        if (primarySeries.isNotEmpty()) {
          lineSeries {
            primarySeries.forEach { s -> series(x = s.xValues, y = s.yValues) }
          }
        } else {
          lineSeries { series(listOf(0.0), listOf(0.0)) }
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
      goalService.getCurrentGoal().collect { goal ->
        val currentAccount = accountService.activeAccount.value
        val rawGoal = currentAccount?.toGoal()
        // Store as display lb (÷10) — unit + weightless applied at display time
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
      entryService.progress.collect { handleIntent(WeightDashboardIntent.SetProgress(it)) }
    }
  }

  private fun subscribeProgressUpdating() {
    viewModelScope.launch {
      entryService.isUpdating.collect { handleIntent(WeightDashboardIntent.SetProgressUpdating(it)) }
    }
  }

  private fun subscribeLatestWeight() {
    viewModelScope.launch {
      entryService.latestEntry.collect { latestEntry ->
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
      entryService.isEmpty.collect { handleIntent(WeightDashboardIntent.SetIsEmpty(it)) }
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
