package com.greatergoods.meapp.features.dashboard.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.common.Progress
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat

/**
 * UI state for the dashboard, holding loading state and entry summaries.
 *
 * @property isLoading Whether data is currently loading.
 * @property visibleKeys List of visible dashboard keys (metrics and milestones).
 * @property dayWiseEntries List of day-wise body scale summaries.
 * @property monthWiseEntries List of month-wise body scale summaries.
 */
data class DashboardState(
  val isLoading: Boolean = false,
  val visibleKeys: List<DashboardKey> = emptyList(),
  val dayWiseEntries: List<PeriodBodyScaleSummary> = emptyList(),
  val monthWiseEntries: List<PeriodBodyScaleSummary> = emptyList(),
  val progress: Progress = Progress()
) : IReducer.State

/**
 * Intent for dashboard actions, such as loading and updating entries.
 */
sealed interface DashboardIntent : IReducer.Intent {
  object LoadEntries : DashboardIntent
  data class SetDayWiseEntries(val entries: List<PeriodBodyScaleSummary>) : DashboardIntent
  data class SetVisibleKeys(val keys: List<DashboardKey>) : DashboardIntent
  data class UpdateVisibleKeys(val keys: List<DashboardKey>) : DashboardIntent
  data class SetMonthWiseEntries(val entries: List<PeriodBodyScaleSummary>) : DashboardIntent
  data class SetIsLoading(val isLoading: Boolean) : DashboardIntent
  data class SetProgress(val progress: Progress) : DashboardIntent
  data class SaveDashboardMetrics(val visibleMetrics: List<Stat>) : DashboardIntent
}

/**
 * Reducer for the dashboard state, handling intents to update entries and loading state.
 */
class DashboardReducer : IReducer<DashboardState, DashboardIntent> {
  override fun reduce(state: DashboardState, intent: DashboardIntent): DashboardState? = when (intent) {
    is DashboardIntent.SetDayWiseEntries -> state.copy(dayWiseEntries = intent.entries)
    is DashboardIntent.SetMonthWiseEntries -> state.copy(monthWiseEntries = intent.entries)
    is DashboardIntent.SetIsLoading -> state.copy(isLoading = intent.isLoading)
    is DashboardIntent.SetVisibleKeys -> state.copy(visibleKeys = intent.keys)
    is DashboardIntent.SetProgress -> state.copy(progress = intent.progress)
    DashboardIntent.LoadEntries -> state.copy(isLoading = true)
    else -> state
  }
}
