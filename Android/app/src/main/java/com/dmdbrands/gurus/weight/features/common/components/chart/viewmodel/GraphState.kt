package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.common.Point as VicoPoint

/**
 * UI state for the graph component, holding all chart-related state variables.
 *
 * @property selectedData List of selected GraphPoint(s) for marker display.
 * @property point Current pointer point for marker interaction.
 * @property xLabels List of X-axis labels.
 * @property ySeries List of Y-axis series data.
 * @property timeStamp List of timestamps for the graph.
 * @property minTarget Minimum target timestamp for the visible range.
 * @property maxTarget Maximum target timestamp for the visible range.
 * @property minYTarget Minimum Y-axis target value.
 * @property secondaryMinYTarget Secondary Y-axis minimum target value.
 * @property maxYTarget Maximum Y-axis target value.
 * @property secondaryMaxYTarget Secondary Y-axis maximum target value.
 * @property selectedTarget Selected target timestamp.
 * @property markerIndex Index of the current marker.
 * @property isUpdating Whether the graph is currently updating.
 * @property modelProducer Chart model producer for Vico library.
 * @property graphKey Hash code of the graph lines for change detection.
 * @property computationJob Current computation job for async operations.
 * @property stepSize Step size for Y-axis scaling.
 * @property scrollState Current scroll state of the chart.
 * @property initialTimeStamp Initial timestamp for the graph.
 * @property todayMills Current timestamp in milliseconds.
 * @property startRangeX Start range for X-axis.
 * @property endRangeX End range for X-axis.
 * @property separators Period separators for the graph.
 * @property isEmpty Whether the graph has no data.
 * @property segment Current graph segment (WEEK, MONTH, etc.).
 * @property goal Current goal for reference.
 * @property graphLines Primary graph lines data.
 * @property secondaryGraphLines Secondary graph lines data.
 */
data class GraphState(
    val selectedData: List<GraphPoint> = emptyList(),
    val point: VicoPoint? = null,
    val xLabels: List<Label> = emptyList(),
    val ySeries: List<List<Label>> = emptyList(),
    val timeStamp: List<Double> = emptyList(),
    val minTarget: Long? = null,
    val maxTarget: Long? = null,
    val minYTarget: Double = 0.0,
    val secondaryMinYTarget: Double = 0.0,
    val maxYTarget: Double = 220.0,
    val secondaryMaxYTarget: Double = 220.0,
    val selectedTarget: Long? = null,
    val markerIndex: Int? = null,
    val isUpdating: Boolean = false,
    val modelProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
    val graphKey: Int = 0,
    val computationJob: kotlinx.coroutines.Job? = null,
    val stepSize: Double = 10.0,
    val scrollState: Scroll = Scroll.Absolute.End,
    val initialTimeStamp: Long = 0L,
    val todayMills: Long = 0L,
    val startRangeX: Long = 0L,
    val endRangeX: Long = 0L,
    val separators: List<Double> = emptyList(),
    val isEmpty: Boolean = true,
    val segment: GraphSegment = GraphSegment.WEEK,
    val goal: Goal? = null,
    val graphLines: List<GraphLine> = emptyList(),
    val secondaryGraphLines: GraphLine? = null,

) : IReducer.State
