package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat

/**
 * UI state for the dashboard, holding loading state and entry summaries.
 *
 * @property isLoading Whether data is currently loading.
 * @property visibleKeys List of visible dashboard keys (metrics and milestones).
 * @property dayWiseEntries List of day-wise body scale summaries.
 * @property monthWiseEntries List of month-wise body scale summaries.
 * @property pagerState Current pager state for horizontal graph navigation.
 * @property dashboardType Current dashboard type (4 or 12 metrics).
 */
data class DashboardState(
  val isLoading: Boolean = false,
  val visibleKeys: List<DashboardKey> = emptyList(),
  val dayWiseEntries: List<PeriodBodyScaleSummary> = emptyList(),
  val monthWiseEntries: List<PeriodBodyScaleSummary> = emptyList(),
  val latestWeight: Double? = null,
  val progress: Progress = Progress(),
  val selectedSegment: GraphSegment = GraphSegment.WEEK,
  val selectedStat: Stat? = null,
  val metricData: List<PeriodBodyScaleSummary> = emptyList(),
  val pagerState: Int = 0,
  val scrollTarget: Double? = null,
  val isRefreshing: Boolean = false,
  val dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS
) : IReducer.State

/**
 * Intent for dashboard actions, such as loading and updating entries.
 */
sealed interface DashboardIntent : IReducer.Intent {
  object LoadEntries : DashboardIntent
  data object Refresh : DashboardIntent
  data class ResetDashboard(val onConfirm: () -> Unit) : DashboardIntent
  data class SetDayWiseEntries(val entries: List<PeriodBodyScaleSummary>) : DashboardIntent
  data class SetVisibleKeys(val keys: List<DashboardKey>) : DashboardIntent
  data class UpdateVisibleKeys(val keys: List<DashboardKey>, val dashboardType: DashboardType) : DashboardIntent
  data class SetMonthWiseEntries(val entries: List<PeriodBodyScaleSummary>) : DashboardIntent
  data class SetIsLoading(val isLoading: Boolean) : DashboardIntent
  data class SetProgress(val progress: Progress) : DashboardIntent
  data class SaveDashboardMetrics(val visibleMetrics: List<Stat>) : DashboardIntent
  data class SetSelectedSegment(val segment: GraphSegment) : DashboardIntent
  data class SetSelectedStat(val stat: Stat?) : DashboardIntent
  data class SetMetricData(val data: List<PeriodBodyScaleSummary>) : DashboardIntent
  data class SetPagerState(val pagerState: Int) : DashboardIntent
  data class SetScrollTarget(val scrollTarget: Double?) : DashboardIntent
  data class UpdateIsRefreshing(val isRefreshing: Boolean) : DashboardIntent
  data class SetDashboardType(val dashboardType: DashboardType) : DashboardIntent
  object OnConnectScale: DashboardIntent
  data class SetLatestWeight(val latestWeight: Double?) : DashboardIntent
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
    is DashboardIntent.SetSelectedSegment -> state.copy(selectedSegment = intent.segment)
    is DashboardIntent.SetSelectedStat -> state.copy(selectedStat = intent.stat)
    is DashboardIntent.SetMetricData -> state.copy(metricData = intent.data)
    is DashboardIntent.SetPagerState -> state.copy(pagerState = intent.pagerState)
    is DashboardIntent.SetScrollTarget -> state.copy(scrollTarget = intent.scrollTarget)
    is DashboardIntent.SetDashboardType -> state.copy(dashboardType = intent.dashboardType)
    is DashboardIntent.LoadEntries -> state.copy(isLoading = true)
    is DashboardIntent.SetLatestWeight -> state.copy(latestWeight = intent.latestWeight)
    else -> state
  }
}
