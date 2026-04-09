package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphReducer
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ── State ──

@Stable
data class WeightDashboardState(
  // Base (chart infrastructure)
  override val dailyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val monthlyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val segmentStates: Map<GraphSegment, SegmentState> = emptyMap(),
  override val selectedSegment: GraphSegment = GraphSegment.WEEK,
  override val scrollTarget: Double? = null,
  override val isRefreshing: Boolean = false,
  override val markerIndex: Double? = null,
  // Weight-specific
  val weightUnit: WeightUnit = WeightUnit.KG,
  val goal: Goal? = null,
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

// ── Intents (extends BaseGraphIntent — inherits all shared intents) ──

sealed interface WeightDashboardIntent : BaseGraphIntent {
  // Weight-only state intents
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

  // Weight-only action intents (side effects in VM)
  data object Refresh : WeightDashboardIntent
  data object OnConnectScale : WeightDashboardIntent
  data object ResetDashboard : WeightDashboardIntent
  data class UpdateVisibleKeys(val keys: List<DashboardKey>, val dashboardType: DashboardType) : WeightDashboardIntent
  data object NavigateToGoal : WeightDashboardIntent
}

// ── Reducer (extends BaseGraphReducer for shared intents) ──

class WeightDashboardReducer : BaseGraphReducer<WeightDashboardState>(), IReducer<WeightDashboardState, BaseGraphIntent> {

  override fun copyBaseFields(
    state: WeightDashboardState,
    segmentStates: Map<GraphSegment, SegmentState>,
    isRefreshing: Boolean,
    markerIndex: Double?,
    selectedSegment: GraphSegment,
    scrollTarget: Double?,
  ) = state.copy(
    segmentStates = segmentStates,
    isRefreshing = isRefreshing,
    markerIndex = markerIndex,
    selectedSegment = selectedSegment,
    scrollTarget = scrollTarget,
  )

  override fun reduce(state: WeightDashboardState, intent: BaseGraphIntent): WeightDashboardState? = when (intent) {
    // Weight-specific intents
    is WeightDashboardIntent -> when (intent) {
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
      // Action intents — no state change
      is WeightDashboardIntent.Refresh -> state
      is WeightDashboardIntent.OnConnectScale -> state
      is WeightDashboardIntent.ResetDashboard -> state
      is WeightDashboardIntent.UpdateVisibleKeys -> state
      is WeightDashboardIntent.NavigateToGoal -> state
    }
    // Base graph intents — delegate to BaseGraphReducer
    else -> reduceBaseIntent(state, intent)
  }
}
