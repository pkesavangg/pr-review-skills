package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphReducer
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

// ── Baby metric toggle ──

enum class BabyMetric { WEIGHT, HEIGHT }

// ── Per-metric state (segments, scroll, marker, percentile) ──

@Stable
data class BabyMetricState(
  val segmentStates: Map<GraphSegment, SegmentState> = emptyMap(),
  val percentileSeries: BabyPercentileHelper.PercentileSeries? = null,
  val selectedSegment: GraphSegment = GraphSegment.WEEK,
  val scrollTarget: Double? = null,
  val markerIndex: Double? = null,
)

// ── State ──

@Stable
data class BabyDashboardState(
  // Shared producers (stable, never swapped)
  override val dailyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val monthlyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  // Per-metric state map
  val metricStates: Map<BabyMetric, BabyMetricState> = emptyMap(),
  val selectedMetric: BabyMetric = BabyMetric.WEIGHT,
  // Shared
  override val isRefreshing: Boolean = false,
  val babyProfile: BabyProfile? = null,
) : BaseDashboardState {
  val activeMetric: BabyMetricState get() = metricStates[selectedMetric] ?: BabyMetricState()

  // Route to active metric
  override val segmentStates: Map<GraphSegment, SegmentState> get() = activeMetric.segmentStates
  override val selectedSegment: GraphSegment get() = activeMetric.selectedSegment
  override val scrollTarget: Double? get() = activeMetric.scrollTarget
  override val markerIndex: Double? get() = activeMetric.markerIndex

  val activePercentileSeries: BabyPercentileHelper.PercentileSeries?
    get() = activeMetric.percentileSeries

  fun metricState(metric: BabyMetric): BabyMetricState =
    metricStates[metric] ?: BabyMetricState()
}

// ── Intents ──

sealed interface BabyDashboardIntent : BaseGraphIntent {
  data class SetBabyProfile(val profile: BabyProfile) : BabyDashboardIntent
  data class SetSelectedMetric(val metric: BabyMetric) : BabyDashboardIntent
  data class SetPercentile(val metric: BabyMetric, val series: BabyPercentileHelper.PercentileSeries?) : BabyDashboardIntent
  data class UpdateMetricSegment(
    val metric: BabyMetric,
    val segment: GraphSegment,
    val update: (SegmentState) -> SegmentState,
  ) : BabyDashboardIntent
  data object Refresh : BabyDashboardIntent
}

// ── Reducer ──

class BabyDashboardReducer : BaseGraphReducer<BabyDashboardState>(), IReducer<BabyDashboardState, BaseGraphIntent> {

  /**
   * Routes base field updates to the active metric's BabyMetricState.
   * selectedSegment, scrollTarget, markerIndex go into the active metric.
   * isRefreshing, producers stay shared.
   */
  override fun copyBaseFields(
    state: BabyDashboardState,
    segmentStates: Map<GraphSegment, SegmentState>,
    isRefreshing: Boolean,
    markerIndex: Double?,
    selectedSegment: GraphSegment,
    scrollTarget: Double?,
  ): BabyDashboardState {
    val current = state.metricState(state.selectedMetric)
    val updated = current.copy(
      segmentStates = segmentStates,
      selectedSegment = selectedSegment,
      scrollTarget = scrollTarget,
      markerIndex = markerIndex,
    )
    return state.copy(
      metricStates = state.metricStates + (state.selectedMetric to updated),
      isRefreshing = isRefreshing,
    )
  }

  override fun reduce(state: BabyDashboardState, intent: BaseGraphIntent): BabyDashboardState? = when (intent) {
    is BabyDashboardIntent -> when (intent) {
      is BabyDashboardIntent.SetBabyProfile -> state.copy(babyProfile = intent.profile)
      is BabyDashboardIntent.SetSelectedMetric -> state.copy(selectedMetric = intent.metric)
      is BabyDashboardIntent.SetPercentile -> {
        val current = state.metricState(intent.metric)
        state.copy(metricStates = state.metricStates + (intent.metric to current.copy(percentileSeries = intent.series)))
      }
      is BabyDashboardIntent.UpdateMetricSegment -> {
        val current = state.metricState(intent.metric)
        val segState = current.segmentStates[intent.segment] ?: SegmentState()
        val updated = current.copy(segmentStates = current.segmentStates + (intent.segment to intent.update(segState)))
        state.copy(metricStates = state.metricStates + (intent.metric to updated))
      }
      is BabyDashboardIntent.Refresh -> state
    }
    else -> reduceBaseIntent(state, intent)
  }
}
