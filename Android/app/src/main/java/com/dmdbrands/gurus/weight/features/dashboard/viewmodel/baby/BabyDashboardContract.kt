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

// ── State ──

@Stable
data class BabyDashboardState(
  override val dailyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val monthlyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val segmentStates: Map<GraphSegment, SegmentState> = emptyMap(),
  override val selectedSegment: GraphSegment = GraphSegment.WEEK,
  override val scrollTarget: Double? = null,
  override val isRefreshing: Boolean = false,
  override val markerIndex: Double? = null,
  val selectedMetric: BabyMetric = BabyMetric.WEIGHT,
  val weightPercentile: BabyPercentileHelper.PercentileSeries? = null,
  val heightPercentile: BabyPercentileHelper.PercentileSeries? = null,
  val babyProfile: BabyProfile? = null,
) : BaseDashboardState {
  val activePercentile: BabyPercentileHelper.PercentileSeries?
    get() = when (selectedMetric) {
      BabyMetric.WEIGHT -> weightPercentile
      BabyMetric.HEIGHT -> heightPercentile
    }
}

// ── Intents ──

sealed interface BabyDashboardIntent : BaseGraphIntent {
  data class SetBabyProfile(val profile: BabyProfile) : BabyDashboardIntent
  data class SetSelectedMetric(val metric: BabyMetric) : BabyDashboardIntent
  data class SetWeightPercentile(val series: BabyPercentileHelper.PercentileSeries?) : BabyDashboardIntent
  data class SetHeightPercentile(val series: BabyPercentileHelper.PercentileSeries?) : BabyDashboardIntent
  data object Refresh : BabyDashboardIntent
}

// ── Reducer ──

class BabyDashboardReducer : BaseGraphReducer<BabyDashboardState>(), IReducer<BabyDashboardState, BaseGraphIntent> {

  override fun copyBaseFields(
    state: BabyDashboardState,
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

  override fun reduce(state: BabyDashboardState, intent: BaseGraphIntent): BabyDashboardState? = when (intent) {
    is BabyDashboardIntent -> when (intent) {
      is BabyDashboardIntent.SetBabyProfile -> state.copy(babyProfile = intent.profile)
      is BabyDashboardIntent.SetSelectedMetric -> state.copy(selectedMetric = intent.metric)
      is BabyDashboardIntent.SetWeightPercentile -> state.copy(weightPercentile = intent.series)
      is BabyDashboardIntent.SetHeightPercentile -> state.copy(heightPercentile = intent.series)
      is BabyDashboardIntent.Refresh -> state
    }
    else -> reduceBaseIntent(state, intent)
  }
}
