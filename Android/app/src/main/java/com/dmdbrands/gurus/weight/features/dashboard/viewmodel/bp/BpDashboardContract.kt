package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphReducer
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

// ── State ──

@Stable
data class BpDashboardState(
  override val dailyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val monthlyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val segmentStates: Map<GraphSegment, SegmentState> = emptyMap(),
  override val selectedSegment: GraphSegment = GraphSegment.WEEK,
  override val scrollTarget: Double? = null,
  override val isRefreshing: Boolean = false,
  override val markerIndex: Double? = null,
  // BP-specific
  val progress: Progress = Progress(),
  val isEmpty: Boolean = false,
) : BaseDashboardState

// ── Intents (extends BaseGraphIntent) ──

sealed interface BpDashboardIntent : BaseGraphIntent {
  // BP-only
  data class SetProgress(val progress: Progress) : BpDashboardIntent
  data class SetIsEmpty(val isEmpty: Boolean) : BpDashboardIntent
  data object Refresh : BpDashboardIntent
}

// ── Reducer ──

class BpDashboardReducer : BaseGraphReducer<BpDashboardState>(), IReducer<BpDashboardState, BaseGraphIntent> {

  override fun copyBaseFields(
    state: BpDashboardState,
    segmentStates: Map<GraphSegment, SegmentState>,
    isRefreshing: Boolean,
    markerIndex: Double?,
    selectedSegment: GraphSegment,
    dailyProducer: CartesianChartModelProducer,
    monthlyProducer: CartesianChartModelProducer,
    scrollTarget: Double?,
  ) = state.copy(
    segmentStates = segmentStates,
    isRefreshing = isRefreshing,
    markerIndex = markerIndex,
    selectedSegment = selectedSegment,
    dailyProducer = dailyProducer,
    monthlyProducer = monthlyProducer,
    scrollTarget = scrollTarget,
  )

  override fun reduce(state: BpDashboardState, intent: BaseGraphIntent): BpDashboardState? = when (intent) {
    is BpDashboardIntent -> when (intent) {
      is BpDashboardIntent.SetProgress -> state.copy(progress = intent.progress)
      is BpDashboardIntent.SetIsEmpty -> state.copy(isEmpty = intent.isEmpty)
      is BpDashboardIntent.Refresh -> state
    }
    else -> reduceBaseIntent(state, intent)
  }
}
