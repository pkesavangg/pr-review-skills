package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import kotlinx.coroutines.Job
import java.util.Calendar

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
  val segment: GraphSegment = GraphSegment.WEEK,
  val goal: Goal? = null,
  val modelProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  val minTarget: Long? = null,
  val maxTarget: Long? = null,
  val minYTarget: Double = 0.0,
  val secondaryMinYTarget: Double = 0.0,
  val maxYTarget: Double = 220.0,
  val secondaryMaxYTarget: Double = 220.0,
  val markerIndex: Int? = null,
  val isUpdating: Boolean = false,
  val computationJob: Job? = null,
  val animationJob: Job? = null,
  val stepSize: Double = 10.0,
  val scrollValue: Double? = null,
  val savedTarget: Long? = null,
  val scrollTarget: Double? = null,
  val separators: List<Double> = emptyList()
) : IReducer.State {
  val graphKey: Int = graphLines.hashCode()
  val xLabels: List<Label> = graphLines.flatMap { graphLine ->
    graphLine.points.map { point ->
      point.x
    }
  }
  val yLabels: List<List<Label>> = graphLines.map { graphLine ->
    graphLine.points.map {
      it.y
    }
  }
  val initialTimeStamp: Long =
    this.xLabels.minOfOrNull { it.value.toDouble() }?.toLong() ?: Calendar.getInstance().timeInMillis
  val endTimeStamp: Long =
    this.xLabels.maxOfOrNull { it.value.toDouble() }?.toLong() ?: Calendar.getInstance().timeInMillis
  val startRangeX = GraphUtil.getStartRange(segment, initialTimeStamp)
  val endRangeX = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)
  val selectedData =
    if (markerIndex != null && markerIndex < xLabels.size) graphLines.map { it.points[markerIndex] } else emptyList()
}
