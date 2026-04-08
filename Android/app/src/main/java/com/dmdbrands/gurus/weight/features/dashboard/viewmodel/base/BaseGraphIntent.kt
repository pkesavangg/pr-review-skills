package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment

/**
 * Graph-related intents shared across all products.
 * Dispatched by GraphView via [BaseDashboardViewModel.handleGraphIntent].
 * Separate from product-specific intents — no inheritance relationship.
 */
sealed interface BaseGraphIntent {
  data class ScrollRange(val segment: GraphSegment, val min: Long, val max: Long) : BaseGraphIntent
  data class UpdateMarkerIndex(val markerIndex: Double?) : BaseGraphIntent
  data class UpdateIsEmptyGraph(val segment: GraphSegment, val isEmpty: Boolean) : BaseGraphIntent
  data class UpdateSegmentTarget(val segment: GraphSegment, val target: List<PeriodBodyScaleSummary>) : BaseGraphIntent
}
