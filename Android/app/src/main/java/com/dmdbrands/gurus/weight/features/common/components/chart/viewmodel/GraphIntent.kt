package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.helper.AxisMeta
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
  data class UpdatePrimaryYAxis(val axisMeta: AxisMeta) : GraphIntent

  /** Update secondary Y-axis */
  data class UpdateSecondaryYAxis(val axisMeta: AxisMeta) : GraphIntent

  /** Update target range for the graph */
  data class UpdateTargetRange(val minTarget: Long?, val maxTarget: Long?) : GraphIntent

  /** Update marker index */
  data class UpdateMarkerIndex(val markerIndex: Int?) : GraphIntent

  data class UpdateSavedTarget(val target: Long) : GraphIntent

  /** Update updating state */
  data class UpdateIsUpdating(val isUpdating: Boolean) : GraphIntent

  /** Update step size */
  data class UpdateStepSize(val stepSize: Double) : GraphIntent

  /** Update scroll state */
  data class UpdateScrollValue(val scrollValue: Double?) : GraphIntent

  /** Update separators */
  data class UpdateSeparators(val separators: List<Double>) : GraphIntent

  /** Update computation job */
  data class UpdateComputationJob(val job: Job?) : GraphIntent

  /** Reset graph state */
  object ResetGraph : GraphIntent

  /** Handle scroll event */
  data class SetScrollRange(val min: Long, val max: Long) : GraphIntent

  /** Set scroll target for frame-synchronized scrolling */
  data class SetScrollTarget(val target: Double?) : GraphIntent
}
