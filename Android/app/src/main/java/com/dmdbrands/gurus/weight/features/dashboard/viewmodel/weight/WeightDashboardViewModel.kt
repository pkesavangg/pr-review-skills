package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toGoal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
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
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
) : BaseDashboardViewModel<WeightDashboardState, BaseGraphIntent>(
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

  override fun handleIntent(intent: BaseGraphIntent) {
    // Weight action intents — side effects
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
    // Base handles graph intents + passes to reducer
    super.handleIntent(intent)
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

  private fun showResetDashboardAlert() {
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
          }
        },
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
