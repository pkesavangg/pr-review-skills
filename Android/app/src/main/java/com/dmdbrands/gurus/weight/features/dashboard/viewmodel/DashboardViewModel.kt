package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the dashboard, managing state and handling dashboard intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 * @property appNavigationService The app event service for observing auth state changes.
 */
@HiltViewModel
class DashboardViewModel
@Inject
constructor(
  private val entryService: IEntryService,
  private val accountService: IAccountService,
  private val appNavigationService: IAppNavigationService,
  private val dashboardService: IDashboardService,
  private val healthConnectService: IHealthConnectService,
  private val goalService: IGoalService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
  reducer = DashboardReducer(),
), DefaultLifecycleObserver {


  init {
    initLoadData()
    viewModelScope.launch {
      subscribeMetrics()
      subscribeDashboardType()
      subscribeProgress()
      subscribeLatestWeight()
      subscribeIsEmpty()
      subscribeWeightLess()
    }
  }

  private fun initLoadData() {
    val activeAccount = accountService.activeAccount.value
    val dashboardType = if (activeAccount?.dashboardType == DashboardType.DASHBOARD_12_METRICS.value)
      DashboardType.DASHBOARD_12_METRICS else DashboardType.DASHBOARD_4_METRICS
    val weightLess = activeAccount.toWeightless()
    val metrics = dashboardService.visibleKeys.value
    super.handleIntent(DashboardIntent.SetDashboardType(dashboardType))
    super.handleIntent(DashboardIntent.SetVisibleKeys(metrics))
    super.handleIntent(DashboardIntent.UpdateWeightLess(weightLess))
  }

  private fun subscribeWeightLess() {
    viewModelScope.launch {
      accountService.activeAccountFlow.map { account ->
        account?.toWeightless()
      }.distinctUntilChanged().collect {
        handleIntent(DashboardIntent.UpdateWeightLess(it))
      }
    }
  }

  override fun onResume(owner: LifecycleOwner) {
    viewModelScope.launch {
      val isOutOfSync = healthConnectService.outOfSyncState.first()
      if (isOutOfSync) {
        healthConnectService.healthConnectOutOfSync()
      }
    }
  }

  override fun provideInitialState(): DashboardState = DashboardState()

  override fun handleIntent(intent: DashboardIntent) {
    when (intent) {
      is DashboardIntent.UpdateVisibleKeys -> updateVisibleKeys(intent.keys, intent.dashboardType)
      is DashboardIntent.ResetDashboard -> showResetDashboardAlert(intent.onConfirm)
      is DashboardIntent.SetPagerState -> handlePagerStateChange(intent.pagerState)
      is DashboardIntent.OnConnectScale -> navigateTo(AppRoute.AccountSettings.AddEditScales)
      is DashboardIntent.SetSelectedStat -> setSelectedStat(intent.stat)
      is DashboardIntent.Refresh -> refresh()

      else -> null
    }
    super.handleIntent(intent)
  }

  private fun setSelectedStat(stat: Stat?) {
    viewModelScope.launch {
      dashboardService.setSelectedKey(stat?.key)
    }
  }

  private fun refresh() {
    viewModelScope.launch {
      handleIntent(DashboardIntent.UpdateIsRefreshing(true))
      entryService.syncOperations()
      dashboardService.refreshDashboard()
      accountService.refreshAccount()
      handleIntent(DashboardIntent.UpdateIsRefreshing(false))
    }
  }

  private fun subscribeIsEmpty() {
    viewModelScope.launch {
      entryService.isEmpty.collect {
        handleIntent(DashboardIntent.UpdateIsEmpty(it))
      }
    }
  }

  private fun subscribeProgress() {
    viewModelScope.launch {
      entryService.progress.collect {
        handleIntent(DashboardIntent.SetProgress(it))
      }
    }
  }

  private fun subscribeLatestWeight() {
    viewModelScope.launch {
      entryService.latestEntry.collect { latestEntry ->
        val latestWeight =
          when (latestEntry) {
            is ScaleEntry -> latestEntry.scale.scaleEntry.weight
            else -> null
          }
        handleIntent(DashboardIntent.SetLatestWeight(latestWeight))
      }
    }
  }

  private fun subscribeMetrics() {
    viewModelScope.launch {
      // Combine both metric and milestone keys into a single DashboardKey list
      dashboardService.visibleKeys.drop(1).collect {
        handleIntent(DashboardIntent.SetVisibleKeys(it))
      }
    }
  }

  private fun subscribeDashboardType() {
    viewModelScope.launch {
      accountService.activeAccountFlow.drop(1).collect { account ->
        if (account != null) {
          val dashboardType = if (account.dashboardType == DashboardType.DASHBOARD_12_METRICS.value)
            DashboardType.DASHBOARD_12_METRICS else DashboardType.DASHBOARD_4_METRICS
          handleIntent(DashboardIntent.SetDashboardType(dashboardType))
        }
      }
    }
  }

  private fun showResetDashboardAlert(onConfirm: () -> Unit) {
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
        dialogQueueService.showLoader(
          message = DashboardString.Loader.Save,
        )
        val currentDashboardType = state.value.dashboardType
        dashboardService.resetVisibleKeys(dashboardType = currentDashboardType)
        // Clear secondary metric selection when resetting dashboard (matching iOS behavior)
        // Clear both UI state (selectedStat) and service state (selectedKey)
        handleIntent(DashboardIntent.SetSelectedStat(null))
      } catch (e: Exception) {
      } finally {
        delay(300)
        dialogQueueService.dismissLoader()
      }
    }
  }

  private fun updateVisibleKeys(keys: List<DashboardKey>, dashboardType: DashboardType) {
    viewModelScope.launch {
      try {
        dialogQueueService.showLoader(
          message = DashboardString.Loader.Save,
        )
        dashboardService.updateVisibleKeys(keys = keys, dashboardType = dashboardType)
        // Clear secondary metric selection when saving changes (matching iOS behavior)
        // Clear both UI state (selectedStat) and service state (selectedKey)
        handleIntent(DashboardIntent.SetSelectedStat(null))
      } catch (e: Exception) {
      } finally {
        delay(300)
        dialogQueueService.dismissLoader()
      }
    }
  }

  /**
   * Saves the dashboard metrics configuration.
   *
   * @param visibleMetrics List of visible metrics to save.
   */
  private fun saveDashboardMetrics(visibleMetrics: List<Stat>) {
    viewModelScope.launch {
      try {
        val metricKeys = visibleMetrics.mapNotNull { stat ->
          when (stat.key) {
            is DashboardKey.Metric -> stat.key.key
            is DashboardKey.Milestone -> null
          }
        }

        dashboardService.updateVisibleMetricKeys(keys = metricKeys)

        dialogQueueService.showToast(
          Toast(
            message = "Dashboard metrics saved successfully",
          ),
        )
      } catch (exception: Exception) {
        dialogQueueService.showToast(
          Toast(
            message = "Failed to save dashboard metrics",
          ),
        )
      }
    }
  }

  /**
   * Handles pager state changes and updates the selected segment accordingly.
   *
   * @param pagerState The new pager state index.
   */
  private fun handlePagerStateChange(pagerState: Int) {
    val segments = GraphSegment.entries
    if (pagerState in segments.indices) {
      val segment = segments[pagerState]
      handleIntent(DashboardIntent.SetSelectedSegment(segment))
    }
  }

  fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }
}
