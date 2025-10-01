package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import kotlinx.coroutines.Job

/**
 * UI state for the graph component, holding all chart-related state variables.
 *
 * @property selectedData List of selected GraphPoint(s) for marker display.
 * @property point Current pointer point for marker interaction.
 * @property xLabels List of X-axis labels.
 * @property yLabels List of Y-axis series data.
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
  val graphLines: List<GraphLine> = emptyList(),
  val secondaryGraphLines: GraphLine? = null,
  val primaryYAxis: CartesianRangeValues? = null,
  val secondaryYAxis: CartesianRangeValues? = null,
  val primaryYStep: Double? = null,
  val goal: Goal? = null,
  val isEmptyGraph : Boolean = false,
  val modelProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  val minTarget: Long? = null,
  val maxTarget: Long? = null,
  val markerIndex: Double? = null,
  val isUpdating: Boolean = false,
  val isLoading: Boolean = false,
  val computationJob: Job? = null,
  val animationJob: Job? = null,
) : IReducer.State {
  val graphKey: Int = graphLines.hashCode()

  // Cached computed properties to avoid repeated calculations
  val xLabels: List<Label> by lazy {
    graphLines.flatMap { graphLine ->
      graphLine.points.map { point -> point.x }
    }
  }

  val yLabels: List<List<Label>> by lazy {
    graphLines.map { graphLine ->
      graphLine.points.map { it.y }
    }
  }

  val initialTimeStamp: Long? by lazy {
    xLabels.minOfOrNull { it.value.toDouble() }?.toLong()
  }

  val endTimeStamp: Long? by lazy {
    xLabels.maxOfOrNull { it.value.toDouble() }?.toLong()
  }

  val selectedData by lazy {
    if (markerIndex != null && markerIndex < xLabels.size) {
      graphLines.mapNotNull { it.points.find { it.x.value.toDouble() == markerIndex } }
    } else {
      emptyList()
    }
  }

  fun getXStartRange(segment: GraphSegment): Long? {
    if (graphLines.isEmpty()) return null
    val xLabels = graphLines.firstNotNullOf { it.points.map { it.x } }
    val initialTimeStamp = xLabels.map { it.value.toLong() }.sorted().min()
    return GraphUtil.getStartRange(segment, initialTimeStamp)
  }

  fun getXEndRange(segment: GraphSegment): Long? {
    if (graphLines.isEmpty()) return null
    val xLabels = graphLines.firstNotNullOf { it.points.map { it.x } }
    val endTimeStamp = xLabels.map { it.value.toLong() }.sorted().max()
    return GraphUtil.getEndRange(segment, endTimeStamp)
  }
}
