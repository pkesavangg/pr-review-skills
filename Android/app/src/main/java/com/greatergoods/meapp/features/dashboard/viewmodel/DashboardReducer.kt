package com.greatergoods.meapp.features.dashboard.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry

/**
 * UI state for the dashboard, holding loading state and entry summaries.
 *
 * @property isLoading Whether data is currently loading.
 * @property dayWiseEntries List of day-wise body scale summaries.
 * @property monthWiseEntries List of month-wise body scale summaries.
 * @property totalEntries List of all scale entries.
 */
data class DashboardState(
    val isLoading: Boolean = false,
    val dayWiseEntries: List<PeriodBodyScaleSummary> = emptyList(),
    val monthWiseEntries: List<PeriodBodyScaleSummary> = emptyList(),
    val totalEntries: List<ScaleEntry> = emptyList(),
) : IReducer.State

/**
 * Intent for dashboard actions, such as loading and updating entries.
 */
sealed interface DashboardIntent : IReducer.Intent {
    object LoadEntries : DashboardIntent
    data class SetDayWiseEntries(val entries: List<PeriodBodyScaleSummary>) : DashboardIntent
    data class SetMonthWiseEntries(val entries: List<PeriodBodyScaleSummary>) : DashboardIntent
    data class SetIsLoading(val isLoading: Boolean) : DashboardIntent
    data class AddEntries(val entries: List<ScaleEntry>) : DashboardIntent
}

/**
 * Reducer for the dashboard state, handling intents to update entries and loading state.
 */
class DashboardReducer : IReducer<DashboardState, DashboardIntent> {
    override fun reduce(state: DashboardState, intent: DashboardIntent): DashboardState? = when (intent) {
        is DashboardIntent.SetDayWiseEntries -> state.copy(dayWiseEntries = intent.entries)
        is DashboardIntent.SetMonthWiseEntries -> state.copy(monthWiseEntries = intent.entries)
        is DashboardIntent.SetIsLoading -> state.copy(isLoading = intent.isLoading)
        is DashboardIntent.AddEntries -> state.copy(totalEntries = state.totalEntries + intent.entries)
        DashboardIntent.LoadEntries -> state.copy(isLoading = true)
    }
}
