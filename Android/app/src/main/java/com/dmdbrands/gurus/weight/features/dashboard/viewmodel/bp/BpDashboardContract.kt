package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
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
  // BP-specific
  val progress: Progress = Progress(),
  val isEmpty: Boolean = false,
) : BaseDashboardState

// ── Intents ──

sealed interface BpDashboardIntent : IReducer.Intent {
  data class UpdateSegment(val segment: GraphSegment, val update: (SegmentState) -> SegmentState) : BpDashboardIntent
  data class SetProducers(val daily: CartesianChartModelProducer, val monthly: CartesianChartModelProducer) : BpDashboardIntent
  data class SetRefreshing(val isRefreshing: Boolean) : BpDashboardIntent
  data class SetSelectedSegment(val segment: GraphSegment) : BpDashboardIntent
  data class SetProgress(val progress: Progress) : BpDashboardIntent
  data class SetIsEmpty(val isEmpty: Boolean) : BpDashboardIntent
}

// ── Reducer ──

class BpDashboardReducer : IReducer<BpDashboardState, BpDashboardIntent> {
  override fun reduce(state: BpDashboardState, intent: BpDashboardIntent): BpDashboardState? = when (intent) {
    is BpDashboardIntent.UpdateSegment -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      state.copy(segmentStates = state.segmentStates + (intent.segment to intent.update(current)))
    }
    is BpDashboardIntent.SetProducers -> state.copy(dailyProducer = intent.daily, monthlyProducer = intent.monthly)
    is BpDashboardIntent.SetRefreshing -> state.copy(isRefreshing = intent.isRefreshing)
    is BpDashboardIntent.SetSelectedSegment -> state.copy(selectedSegment = intent.segment)
    is BpDashboardIntent.SetProgress -> state.copy(progress = intent.progress)
    is BpDashboardIntent.SetIsEmpty -> state.copy(isEmpty = intent.isEmpty)
  }
}
