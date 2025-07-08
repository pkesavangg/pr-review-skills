package com.greatergoods.meapp.features.dashboard.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.service.IAppNavigationService
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

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
  private val appNavigationService: IAppNavigationService,
  private val dashboardService: IDashboardService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
  reducer = DashboardReducer(),
) {
  init {
    handleIntent(DashboardIntent.LoadEntries)
    loadEntries()
    subscribeMetrics()
    viewModelScope.launch {
      entryService.progress.collect {
        Log.d("DashboardViewModel", "Progress: $it")
      }
    }
  }

  override fun provideInitialState(): DashboardState = DashboardState()

  override fun handleIntent(intent: DashboardIntent) {
    when (intent) {
      is DashboardIntent.UpdateVisibleKeys -> updateVisibleKeys(intent.keys)
      is DashboardIntent.SaveDashboardMetrics -> saveDashboardMetrics(intent.visibleMetrics)
      else -> null
    }
    super.handleIntent(intent)
  }

  private fun subscribeMetrics() {
    viewModelScope.launch {
      // Combine both metric and milestone keys into a single DashboardKey list
      combine(
        dashboardService.getVisibleMetricKeys(),
        dashboardService.getVisibleMilestoneKeys(),
      ) { metricKeys, milestoneKeys ->
        val combinedKeys = mutableListOf<DashboardKey>()
        combinedKeys.addAll(metricKeys.map { DashboardKey.Metric(it) })
        combinedKeys.addAll(milestoneKeys.map { DashboardKey.Milestone(it) })
        combinedKeys
      }.collect {
        handleIntent(DashboardIntent.SetVisibleKeys(it))
      }
    }
  }

  private fun updateVisibleKeys(keys: List<DashboardKey>) {
    viewModelScope.launch {
      val metricKeys = keys.filterIsInstance<DashboardKey.Metric>().map { it.key }

      val milestoneKeys = keys.filterIsInstance<DashboardKey.Milestone>().map { it.key }


      dashboardService.updateVisibleMetricKeys(keys = metricKeys)
      dashboardService.updateVisibleMilestoneKeys(keys = milestoneKeys)
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
  private fun loadEntries() {
    viewModelScope.launch {
      entryService.getDaywiseBodyScaleLatestWithJoin().collect { dayWise ->
        handleIntent(DashboardIntent.SetDayWiseEntries(dayWise))
      }
    }
    viewModelScope.launch {
      entryService.getMonthlyBodyScaleAveragesWithJoin().collect { monthWise ->
        handleIntent(DashboardIntent.SetMonthWiseEntries(monthWise))
      }
    }
    viewModelScope.launch {
      handleIntent(DashboardIntent.SetIsLoading(entryService.isUpdating.value))
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
