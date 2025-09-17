package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import kotlinx.coroutines.Job

/**
 * Intent for graph actions, defining all possible user interactions and state updates.
 */
sealed interface GraphIntent : IReducer.Intent {
  /** Initialize the graph with new data */
  data class InitializeGraph(
    val graphLines: List<GraphLine>,
    val secondaryGraphLines: GraphLine? = null,
    val goal: Goal? = null,
  ) : GraphIntent

  /** Update primary Y-axis */
  data class UpdatePrimaryYAxis(val yRangeValues: CartesianRangeValues) : GraphIntent

  /** Update secondary Y-axis */
  data class UpdateSecondaryYAxis(val yRangeValues: CartesianRangeValues) : GraphIntent

  /** Update marker index */
  data class UpdateMarkerIndex(val markerIndex: Double?) : GraphIntent

  /** Update updating state */
  data class UpdateIsUpdating(val isUpdating: Boolean) : GraphIntent

  /** Update computation job */
  data class UpdateComputationJob(val job: Job?) : GraphIntent

  /** Reset graph state */
  object ResetGraph : GraphIntent

  /** Handle scroll event */
  data class SetScrollRange(val min: Long, val max: Long) : GraphIntent
}
