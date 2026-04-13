package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Per-segment chart UI state. Holds range info, markers, and
 * data/target entries (as PeriodBodyScaleSummary — adapters map product data into this).
 */
@Stable
data class SegmentState(
  val data: ImmutableList<PeriodSummary> = persistentListOf(),
  val target: ImmutableList<PeriodSummary> = persistentListOf(),
  val chartMinX: Double? = null,
  val chartMaxX: Double? = null,
  val isEmptyGraph: Boolean = false,
  val isSingleWindow: Boolean = false,
  val startTimestamp: Long? = null,
  val endTimestamp: Long? = null,
  /** Current visible X range from scroll — updated on scroll stop. */
  val visibleMin: Long? = null,
  val visibleMax: Long? = null,
  /** Last settled Y range from ScrollAwareRangeProvider — seeds frame-0 on segment switch. */
  val seedMinY: Double? = null,
  val seedMaxY: Double? = null,
)

/**
 * Base dashboard state interface. Pure chart infrastructure.
 * No product-specific fields (weightUnit, goal, data, target).
 * Those live in product-specific state subclasses.
 */
interface BaseDashboardState : IReducer.State {
  val dailyProducer: CartesianChartModelProducer
  val monthlyProducer: CartesianChartModelProducer
  val segmentStates: Map<GraphSegment, SegmentState>
  val selectedSegment: GraphSegment
  val scrollTarget: Double?
  val isRefreshing: Boolean
  val markerIndex: Double?

  fun forSegment(segment: GraphSegment): SegmentState {
    return segmentStates[segment] ?: SegmentState()
  }

  fun producerForSegment(segment: GraphSegment): CartesianChartModelProducer {
    return if (segment == GraphSegment.WEEK || segment == GraphSegment.MONTH) dailyProducer else monthlyProducer
  }
}
