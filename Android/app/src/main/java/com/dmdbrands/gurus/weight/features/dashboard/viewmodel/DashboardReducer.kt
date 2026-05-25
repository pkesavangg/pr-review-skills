package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
@Stable
data class DashboardState(
  val visibleKeys: ImmutableList<DashboardKey> = persistentListOf(),
  val data: ImmutableList<PeriodBodyScaleSummary> = persistentListOf(),
  val latestWeight: Double? = null,
  val progress: Progress = Progress(),
  val isProgressUpdating: Boolean = false,
  val selectedSegment: GraphSegment = GraphSegment.DEFAULT,
  val selectedStat: Stat? = null,
  val pagerState: Int = 0,
  val isEmpty: Boolean = false,
  val isRefreshing: Boolean = false,
  val weightless: Weightless? = null,
  val isConsuming: Boolean = false,
  val dashboardType: DashboardType = DashboardType.DASHBOARD_4_METRICS
) : IReducer.State

/**
 * Intent for dashboard actions, such as loading and updating entries.
 */
sealed interface DashboardIntent : IReducer.Intent {

  data class UpdateIsEmpty(val isEmpty: Boolean) : DashboardIntent
  data object Refresh : DashboardIntent
  data class ResetDashboard(val onConfirm: () -> Unit) : DashboardIntent
  data class SetVisibleKeys(val keys: List<DashboardKey>) : DashboardIntent
  data class UpdateVisibleKeys(val keys: List<DashboardKey>, val dashboardType: DashboardType) : DashboardIntent
  data class SetProgress(val progress: Progress) : DashboardIntent
  data class SetProgressUpdating(val isUpdating: Boolean) : DashboardIntent

  /** Switches the selected segment. The new segment resets to its default range (latest entry at right edge). */
  data class SetSelectedSegment(val segment: GraphSegment) : DashboardIntent

  data class SetSelectedStat(val stat: Stat?) : DashboardIntent

  data class SetData(val data: List<PeriodBodyScaleSummary>) : DashboardIntent
  @Stable
  data class SetPagerState(val pagerState: Int) : DashboardIntent
  data class SetIsChartConsuming(val isConsuming: Boolean) : DashboardIntent
  data class SetDashboardType(val dashboardType: DashboardType) : DashboardIntent
  data class UpdateIsRefreshing(val isRefreshing: Boolean) : DashboardIntent
  object OnConnectScale : DashboardIntent
  data class SetLatestWeight(val latestWeight: Double?) : DashboardIntent
  data class UpdateWeightLess(val weightless: Weightless?) : DashboardIntent
}

/**
 * Reducer for the dashboard state, handling intents to update entries and loading state.
 */
class DashboardReducer : IReducer<DashboardState, DashboardIntent> {
  override fun reduce(state: DashboardState, intent: DashboardIntent): DashboardState? = when (intent) {
    is DashboardIntent.UpdateIsRefreshing -> state.copy(isRefreshing = intent.isRefreshing)
    is DashboardIntent.UpdateIsEmpty -> state.copy(isEmpty = intent.isEmpty)
    is DashboardIntent.SetVisibleKeys -> state.copy(visibleKeys = intent.keys.toImmutableList())
    is DashboardIntent.SetProgress -> state.copy(progress = intent.progress)
    is DashboardIntent.SetProgressUpdating -> state.copy(isProgressUpdating = intent.isUpdating)
    is DashboardIntent.SetSelectedSegment -> if (intent.segment == state.selectedSegment) {
      state
    } else {
      state.copy(selectedSegment = intent.segment)
    }

    is DashboardIntent.SetIsChartConsuming -> state.copy(isConsuming = intent.isConsuming)
    is DashboardIntent.SetSelectedStat -> state.copy(selectedStat = intent.stat)
    is DashboardIntent.SetData -> state.copy(data = intent.data.toImmutableList())
    is DashboardIntent.SetPagerState -> state.copy(pagerState = intent.pagerState)
    is DashboardIntent.SetDashboardType -> state.copy(dashboardType = intent.dashboardType)
    is DashboardIntent.SetLatestWeight -> state.copy(latestWeight = intent.latestWeight)
    is DashboardIntent.UpdateWeightLess -> state.copy(weightless = intent.weightless)
    else -> state
  }
}
