package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.common.Point as VicoPoint

/**
 * Intent for graph actions, defining all possible user interactions and state updates.
 */
sealed interface GraphIntent : IReducer.Intent {
    /** Initialize the graph with new data */
    data class InitializeGraph(
        val graphLines: List<GraphLine>,
        val secondaryGraphLines: GraphLine? = null,
        val segment: GraphSegment = GraphSegment.WEEK,
        val goal: Goal? = null
    ) : GraphIntent

    /** Update the graph segment */
    data class UpdateSegment(val segment: GraphSegment) : GraphIntent

    /** Update selected data points */
    data class UpdateSelectedData(val selectedData: List<GraphPoint>) : GraphIntent

    /** Update pointer point for marker interaction */
    data class UpdatePoint(val point: VicoPoint?) : GraphIntent

    /** Update target range for the graph */
    data class UpdateTargetRange(val minTarget: Long?, val maxTarget: Long?) : GraphIntent

    /** Update Y-axis targets */
    data class UpdateYAxisTargets(
        val minYTarget: Double,
        val maxYTarget: Double,
        val secondaryMinYTarget: Double = minYTarget,
        val secondaryMaxYTarget: Double = maxYTarget
    ) : GraphIntent

    /** Update selected target timestamp */
    data class UpdateSelectedTarget(val selectedTarget: Long?) : GraphIntent

    /** Update marker index */
    data class UpdateMarkerIndex(val markerIndex: Int?) : GraphIntent

    /** Update updating state */
    data class UpdateIsUpdating(val isUpdating: Boolean) : GraphIntent

    /** Update step size */
    data class UpdateStepSize(val stepSize: Double) : GraphIntent

    /** Update scroll state */
    data class UpdateScrollState(val scrollState: Scroll) : GraphIntent

    /** Update initial timestamp */
    data class UpdateInitialTimestamp(val initialTimestamp: Long) : GraphIntent

    /** Update today's timestamp */
    data class UpdateTodayMills(val todayMills: Long) : GraphIntent

    /** Update range values */
    data class UpdateRangeValues(val startRangeX: Long, val endRangeX: Long) : GraphIntent

    /** Update separators */
    data class UpdateSeparators(val separators: List<Double>) : GraphIntent

    /** Update empty state */
    data class UpdateIsEmpty(val isEmpty: Boolean) : GraphIntent

    /** Update computation job */
    data class UpdateComputationJob(val job: kotlinx.coroutines.Job?) : GraphIntent

    /** Update graph key */
    data class UpdateGraphKey(val graphKey: Int) : GraphIntent

    /** Update X labels */
    data class UpdateXLabels(val xLabels: List<Label>) : GraphIntent

    /** Update Y series */
    data class UpdateYSeries(val ySeries: List<List<Label>>) : GraphIntent

    /** Update timestamps */
    data class UpdateTimestamps(val timeStamp: List<Double>) : GraphIntent

    /** Reset graph state */
    object ResetGraph : GraphIntent

    /** Handle scroll event */
    data class HandleScroll(val min: Long, val max: Long) : GraphIntent

    /** Handle marker selection */
    data class HandleMarkerSelection(val selectedData: List<GraphPoint>) : GraphIntent
}
