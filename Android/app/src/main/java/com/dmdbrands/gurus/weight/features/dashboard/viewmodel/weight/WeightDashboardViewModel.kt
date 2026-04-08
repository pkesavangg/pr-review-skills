package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight

import androidx.compose.runtime.Stable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toGoal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.WeightGraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ──

@Stable
data class WeightDashboardState(
  // Base graph state
  override val dailyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val monthlyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val segmentStates: Map<GraphSegment, SegmentState> = emptyMap(),
  override val selectedSegment: GraphSegment = GraphSegment.WEEK,
  override val scrollTarget: Double? = null,
  override val isRefreshing: Boolean = false,
  override val weightUnit: WeightUnit = WeightUnit.KG,
  override val goal: Goal? = null,
  // Weight-specific content
  val data: ImmutableList<PeriodBodyScaleSummary> = persistentListOf(),
  val visibleKeys: ImmutableList<DashboardKey> = persistentListOf(),
  val selectedStat: Stat? = null,
  val latestWeight: Double? = null,
  val progress: Progress = Progress(),
  val isProgressUpdating: Boolean = false,
  val isEmpty: Boolean = false,
  val dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS,
  val secondaryKey: DashboardKey? = null,
) : BaseDashboardState

// ── Intents ──

sealed interface WeightDashboardIntent : IReducer.Intent {
  // Graph segment intents
  data class UpdateSegment(val segment: GraphSegment, val update: (SegmentState) -> SegmentState) : WeightDashboardIntent
  data class SetProducers(val daily: CartesianChartModelProducer, val monthly: CartesianChartModelProducer) : WeightDashboardIntent
  data class SetRefreshing(val isRefreshing: Boolean) : WeightDashboardIntent
  data class SetSelectedSegment(val segment: GraphSegment) : WeightDashboardIntent

  // Weight content intents
  data class SetData(val data: List<PeriodBodyScaleSummary>) : WeightDashboardIntent
  data class SetVisibleKeys(val keys: List<DashboardKey>) : WeightDashboardIntent
  data class SetSelectedStat(val stat: Stat?) : WeightDashboardIntent
  data class SetLatestWeight(val latestWeight: Double?) : WeightDashboardIntent
  data class SetProgress(val progress: Progress) : WeightDashboardIntent
  data class SetProgressUpdating(val isUpdating: Boolean) : WeightDashboardIntent
  data class SetIsEmpty(val isEmpty: Boolean) : WeightDashboardIntent
  data class SetDashboardType(val dashboardType: DashboardType) : WeightDashboardIntent
  data class SetGoal(val goal: Goal?) : WeightDashboardIntent
  data class SetWeightUnit(val weightUnit: WeightUnit) : WeightDashboardIntent
  data class SetSecondaryKey(val key: DashboardKey?) : WeightDashboardIntent
}

// ── Reducer ──

class WeightDashboardReducer : IReducer<WeightDashboardState, WeightDashboardIntent> {
  override fun reduce(state: WeightDashboardState, intent: WeightDashboardIntent): WeightDashboardState? = when (intent) {
    is WeightDashboardIntent.UpdateSegment -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      state.copy(segmentStates = state.segmentStates + (intent.segment to intent.update(current)))
    }
    is WeightDashboardIntent.SetProducers -> state.copy(dailyProducer = intent.daily, monthlyProducer = intent.monthly)
    is WeightDashboardIntent.SetRefreshing -> state.copy(isRefreshing = intent.isRefreshing)
    is WeightDashboardIntent.SetSelectedSegment -> state.copy(selectedSegment = intent.segment)
    is WeightDashboardIntent.SetData -> state.copy(data = intent.data.toImmutableList())
    is WeightDashboardIntent.SetVisibleKeys -> state.copy(visibleKeys = intent.keys.toImmutableList())
    is WeightDashboardIntent.SetSelectedStat -> state.copy(selectedStat = intent.stat)
    is WeightDashboardIntent.SetLatestWeight -> state.copy(latestWeight = intent.latestWeight)
    is WeightDashboardIntent.SetProgress -> state.copy(progress = intent.progress)
    is WeightDashboardIntent.SetProgressUpdating -> state.copy(isProgressUpdating = intent.isUpdating)
    is WeightDashboardIntent.SetIsEmpty -> state.copy(isEmpty = intent.isEmpty)
    is WeightDashboardIntent.SetDashboardType -> state.copy(dashboardType = intent.dashboardType)
    is WeightDashboardIntent.SetGoal -> state.copy(goal = intent.goal)
    is WeightDashboardIntent.SetWeightUnit -> state.copy(weightUnit = intent.weightUnit)
    is WeightDashboardIntent.SetSecondaryKey -> state.copy(secondaryKey = intent.key)
  }
}

// ── ViewModel ──

@HiltViewModel
class WeightDashboardViewModel @Inject constructor(
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val dashboardService: IDashboardService,
  private val goalService: IGoalService,
  private val healthConnectService: IHealthConnectService,
) : BaseDashboardViewModel<WeightDashboardState, WeightDashboardIntent>(
  reducer = WeightDashboardReducer(),
), DefaultLifecycleObserver {

  companion object {
    private const val TAG = "WeightDashboardVM"
  }

  override val adapter: GraphDataAdapter = WeightGraphDataAdapter()

  override fun getDailyDataFlow(): Flow<GraphData> =
    entryService.daywiseBodyScaleAverages.map { GraphData.Weight(it) }

  override fun getMonthlyDataFlow(): Flow<GraphData> =
    entryService.monthlyBodyScaleAverages.map { GraphData.Weight(it) }

  override fun updateSegmentState(segment: GraphSegment, update: (SegmentState) -> SegmentState) {
    handleIntent(WeightDashboardIntent.UpdateSegment(segment, update))
  }

  override fun setProducers(daily: CartesianChartModelProducer, monthly: CartesianChartModelProducer) {
    handleIntent(WeightDashboardIntent.SetProducers(daily, monthly))
  }

  override fun setRefreshing(isRefreshing: Boolean) {
    handleIntent(WeightDashboardIntent.SetRefreshing(isRefreshing))
  }

  override fun provideInitialState(): WeightDashboardState = WeightDashboardState()

  init {
    initLoadData()
    startGraphSubscriptions()
    subscribeWeightUnit()
    subscribeGoal()
  }

  override fun onDependenciesReady() {
    subscribeMetrics()
    subscribeDashboardType()
    subscribeProgress()
    subscribeProgressUpdating()
    subscribeLatestWeight()
    subscribeIsEmpty()
  }

  override fun onResume(owner: LifecycleOwner) {
    viewModelScope.launch {
      val isOutOfSync = healthConnectService.outOfSyncState.first()
      if (isOutOfSync) healthConnectService.healthConnectOutOfSync()
    }
  }

  fun refresh() {
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

  // ── Weight-specific subscriptions ──

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

  private fun subscribeGoal() {
    viewModelScope.launch {
      goalService.getCurrentGoal().collect { goal ->
        val currentAccount = accountService.activeAccount.value
        val processedGoal = currentAccount?.toGoal()?.let { g ->
          val weightUnit = currentAccount.weightUnit
          val weightless = currentAccount.toWeightless()
          g.process(weightUnit, weightless)
        }
        handleIntent(WeightDashboardIntent.SetGoal(processedGoal))
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

  fun setSelectedStat(stat: Stat?) {
    viewModelScope.launch {
      dashboardService.setSelectedKey(stat?.key)
    }
    handleIntent(WeightDashboardIntent.SetSelectedStat(stat))
  }

  fun showResetDashboardAlert(onConfirm: () -> Unit) {
    val string = DashboardString.ResetDialog
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = string.Title,
        message = string.Message,
        confirmText = string.ConfirmText,
        cancelText = string.CancelText,
        onConfirm = {
          viewModelScope.launch {
            resetDashboard()
            onConfirm()
          }
        },
      ),
    )
  }

  private fun resetDashboard() {
    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(message = DashboardString.Loader.Save)
        val currentDashboardType = state.value.dashboardType
        dashboardService.resetVisibleKeys(dashboardType = currentDashboardType)
        handleIntent(WeightDashboardIntent.SetSelectedStat(null))
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to reset dashboard", e)
      } finally {
        delay(300)
        dialogQueueService.dismissLoader()
      }
    }
  }

  fun updateVisibleKeys(keys: List<DashboardKey>, dashboardType: DashboardType) {
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

  fun navigateTo(route: AppRoute) {
    viewModelScope.launch { navigationService.navigateTo(route) }
  }
}
