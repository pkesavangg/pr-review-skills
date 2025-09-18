package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
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
import kotlinx.coroutines.flow.first
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
  private val goalService: IGoalService,
  private val appNavigationService: IAppNavigationService,
  private val dashboardService: IDashboardService,
  private val healthConnectService: IHealthConnectService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
  reducer = DashboardReducer(),
), DefaultLifecycleObserver {
  init {
    viewModelScope.launch {
      handleIntent(DashboardIntent.LoadEntries)
      subscribeMetrics()
      loadEntries()
      subscribeProgress()
      subscribeGoals()
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
      is DashboardIntent.UpdateVisibleKeys -> updateVisibleKeys(intent.keys)
      is DashboardIntent.ResetDashboard -> resetDashboard(intent.onConfirm)
      is DashboardIntent.SaveDashboardMetrics -> saveDashboardMetrics(intent.visibleMetrics)
      is DashboardIntent.SetPagerState -> handlePagerStateChange(intent.pagerState)
      else -> null
    }
    super.handleIntent(intent)
  }

  private fun subscribeProgress() {
    viewModelScope.launch {
      entryService.progress.collect {
        handleIntent(DashboardIntent.SetProgress(it))
      }
    }
  }

  private fun subscribeGoals() {
    viewModelScope.launch {
      goalService.getCurrentGoal().collect {
        handleIntent(DashboardIntent.SetGoal(it))
      }
    }
  }

  private fun subscribeMetrics() {
    viewModelScope.launch {
      // Combine both metric and milestone keys into a single DashboardKey list
      dashboardService.visibleKeys.collect {
        handleIntent(DashboardIntent.SetVisibleKeys(it))
      }
    }
  }

  private fun resetDashboard(onConfirm: () -> Unit) {
    val string = DashboardString.ResetDialog
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = string.Title,
        message = string.Message,
        confirmText = string.ConfirmText,
        cancelText = string.CancelText,
        onConfirm = {
          viewModelScope.launch {
            onConfirm()
            dashboardService.resetVisibleKeys()
          }
        },
      ),
    )
  }

  private fun updateVisibleKeys(keys: List<DashboardKey>) {
    try {
      viewModelScope.launch {
        dialogQueueService.showLoader(
          message = DashboardString.Loader.Save,
        )
        dashboardService.updateVisibleKeys(keys = keys)
      }
    } catch (e: Exception) {
    } finally {
      dialogQueueService.dismissLoader()
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
   * Loads entries and updates the state accordingly.
   */
  private suspend fun loadEntries() {
    viewModelScope.launch {
      viewModelScope.launch {
        entryService.daywiseBodyScaleAverages.collect { dayWise ->
          handleIntent(DashboardIntent.SetDayWiseEntries(dayWise))
        }
      }
      viewModelScope.launch {
        entryService.monthlyBodyScaleAverages.collect { monthWise ->
          handleIntent(DashboardIntent.SetMonthWiseEntries(monthWise))
        }
      }
      viewModelScope.launch {
        handleIntent(DashboardIntent.SetIsLoading(entryService.isUpdating.value))
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

  /**
   * Adds new entries using the entry service and updates the state.
   *
   * @param entries The list of entries to add.
   */
  fun addEntry(entries: List<ScaleEntry>) {
    viewModelScope.launch {
      dialogQueueService.showToast(
        Toast(
          message = "Adding ${entries.size} entries",
        ),
      )
    }
  }
}
