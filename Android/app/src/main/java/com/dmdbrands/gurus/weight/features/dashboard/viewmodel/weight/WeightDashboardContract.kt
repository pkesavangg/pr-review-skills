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

// ── Intents ──

sealed interface WeightDashboardIntent : IReducer.Intent {
  data class UpdateSegment(val segment: GraphSegment, val update: (SegmentState) -> SegmentState) : WeightDashboardIntent
  data class SetProducers(val daily: CartesianChartModelProducer, val monthly: CartesianChartModelProducer) : WeightDashboardIntent
  data class SetRefreshing(val isRefreshing: Boolean) : WeightDashboardIntent
  data class SetSelectedSegment(val segment: GraphSegment) : WeightDashboardIntent

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
  data class UpdateMarkerIndex(val markerIndex: Double?) : WeightDashboardIntent
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
    is WeightDashboardIntent.UpdateMarkerIndex -> state.copy(markerIndex = intent.markerIndex)
  }
}
