package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.collections.immutable.toImmutableList

// ── Base Intent ──

/**
 * Shared intents for all product dashboards.
 * Product intents extend this — so all product intents ARE BaseGraphIntents.
 */
interface BaseGraphIntent : IReducer.Intent {
  data class UpdateSegment(val segment: GraphSegment, val update: (SegmentState) -> SegmentState) : BaseGraphIntent
  data class SetRefreshing(val isRefreshing: Boolean) : BaseGraphIntent
  data class SetSelectedSegment(val segment: GraphSegment, val anchorTimestamp: Double? = null) : BaseGraphIntent
  data class UpdateMarkerIndex(val markerIndex: Double?) : BaseGraphIntent
  data class ScrollRange(val segment: GraphSegment, val min: Long, val max: Long, val onFallback: () -> Unit = {}) : BaseGraphIntent
  data class UpdateIsEmptyGraph(val segment: GraphSegment, val isEmpty: Boolean) : BaseGraphIntent
  data class UpdateSegmentTarget(val segment: GraphSegment, val target: List<PeriodSummary>) : BaseGraphIntent
  data class UpdateSeedYRange(val segment: GraphSegment, val minY: Double, val maxY: Double) : BaseGraphIntent
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
    scrollTarget: Double? = state.scrollTarget,
  ): S

  fun reduceBaseIntent(state: S, intent: BaseGraphIntent): S = when (intent) {
    is BaseGraphIntent.UpdateSegment -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(state, segmentStates = state.segmentStates + (intent.segment to intent.update(current)))
    }

    is BaseGraphIntent.SetRefreshing -> copyBaseFields(state, isRefreshing = intent.isRefreshing)
    is BaseGraphIntent.SetSelectedSegment -> copyBaseFields(state, selectedSegment = intent.segment, scrollTarget = intent.anchorTimestamp, markerIndex = null)
    is BaseGraphIntent.UpdateMarkerIndex -> copyBaseFields(state, markerIndex = intent.markerIndex)
    is BaseGraphIntent.ScrollRange -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(
        state,
        segmentStates = state.segmentStates + (intent.segment to current.copy(
          visibleMin = intent.min,
          visibleMax = intent.max,
        )),
      )
    }

    is BaseGraphIntent.UpdateIsEmptyGraph -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(
        state,
        segmentStates = state.segmentStates + (intent.segment to current.copy(isEmptyGraph = intent.isEmpty)),
      )
    }

    is BaseGraphIntent.UpdateSegmentTarget -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      // Clear markerIndex if the saved position is outside the new target's data range
      val clearMarker = state.markerIndex?.let { idx ->
        val timestamps = intent.target.map { it.getTimeStamp() }
        timestamps.isNotEmpty() && idx.toLong() !in timestamps.min()..timestamps.max()
      } ?: false
      copyBaseFields(
        state,
        segmentStates = state.segmentStates + (intent.segment to current.copy(target = intent.target.toImmutableList())),
        markerIndex = if (clearMarker) null else state.markerIndex,
      )
    }

    is BaseGraphIntent.UpdateSeedYRange -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      copyBaseFields(
        state,
        segmentStates = state.segmentStates + (intent.segment to current.copy(
          seedMinY = intent.minY,
          seedMaxY = intent.maxY,
        )),
      )
    }

    else -> state // product-specific intents not handled here
  }
}
