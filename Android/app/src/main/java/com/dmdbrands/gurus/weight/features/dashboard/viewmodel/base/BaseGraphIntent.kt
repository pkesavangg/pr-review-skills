package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import kotlinx.collections.immutable.toImmutableList

// ── Base Intent ──

/**
 * Shared intents for all product dashboards.
 * Product intents extend this — so all product intents ARE BaseGraphIntents.
 */
interface BaseGraphIntent : IReducer.Intent {
  data class UpdateSegment(val segment: GraphSegment, val update: (SegmentState) -> SegmentState) : BaseGraphIntent
  data class SetProducers(val daily: CartesianChartModelProducer, val monthly: CartesianChartModelProducer) : BaseGraphIntent
  data class SetRefreshing(val isRefreshing: Boolean) : BaseGraphIntent
  data class SetSelectedSegment(val segment: GraphSegment) : BaseGraphIntent
  data class UpdateMarkerIndex(val markerIndex: Double?) : BaseGraphIntent
  data class ScrollRange(val segment: GraphSegment, val min: Long, val max: Long) : BaseGraphIntent
  data class UpdateIsEmptyGraph(val segment: GraphSegment, val isEmpty: Boolean) : BaseGraphIntent
  data class UpdateSegmentTarget(val segment: GraphSegment, val target: List<PeriodBodyScaleSummary>) : BaseGraphIntent
}

// ── Base Reducer ──

/**
 * Reduces [BaseGraphIntent] for any product state.
 * Product reducers extend this and implement [copyBaseFields].
 * Product reducer's `reduce()` calls `reduceBaseIntent()` for base intents.
 */
abstract class BaseGraphReducer<S : BaseDashboardState> {

  /**
   * Product implements — applies base field changes via data class .copy().
   * Only the changed fields differ from current state.
   */
  protected abstract fun copyBaseFields(
    state: S,
    segmentStates: Map<GraphSegment, SegmentState> = state.segmentStates,
    isRefreshing: Boolean = state.isRefreshing,
    markerIndex: Double? = state.markerIndex,
    selectedSegment: GraphSegment = state.selectedSegment,
    dailyProducer: CartesianChartModelProducer = state.dailyProducer,
    monthlyProducer: CartesianChartModelProducer = state.monthlyProducer,
    scrollTarget: Double? = state.scrollTarget,
  ): S

  fun reduceBaseIntent(state: S, intent: BaseGraphIntent): S = when (intent) {
    is BaseGraphIntent.UpdateSegment -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(state, segmentStates = state.segmentStates + (intent.segment to intent.update(current)))
    }
    is BaseGraphIntent.SetProducers -> copyBaseFields(state, dailyProducer = intent.daily, monthlyProducer = intent.monthly)
    is BaseGraphIntent.SetRefreshing -> copyBaseFields(state, isRefreshing = intent.isRefreshing)
    is BaseGraphIntent.SetSelectedSegment -> copyBaseFields(state, selectedSegment = intent.segment)
    is BaseGraphIntent.UpdateMarkerIndex -> copyBaseFields(state, markerIndex = intent.markerIndex)
    is BaseGraphIntent.ScrollRange -> state // side effect handled in VM
    is BaseGraphIntent.UpdateIsEmptyGraph -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(state, segmentStates = state.segmentStates + (intent.segment to current.copy(isEmptyGraph = intent.isEmpty)))
    }
    is BaseGraphIntent.UpdateSegmentTarget -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(state, segmentStates = state.segmentStates + (intent.segment to current.copy(target = intent.target.toImmutableList())))
    }
    else -> state // product-specific intents not handled here
  }
}
