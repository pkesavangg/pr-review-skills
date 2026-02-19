package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.bottomAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.endAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.startAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.topAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphSnapHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.visibleLabelsCount
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberFadingEdges
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import java.util.Calendar

@Composable
fun rememberGraphChart(
  state: GraphState,
  defaultMarker: CartesianMarker,
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  onChartClick: ((List<Double>, Double?) -> Unit)? = null,
  handleIntent: (GraphIntent) -> Unit,
): CartesianChart {
  // Get weightless mode from goal's account if available
  val isWeightlessOn = state.goal?.account?.isWeightlessOn ?: false
  val goalMarker = rememberGoalMarker(goal = state.goal, isWeightlessOn = isWeightlessOn)
  val markerIndex = state.markerIndex
  val timeStamps = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted()
  // Visible labels count per segment (TOTAL uses dynamic range)
  val visibleLabelsCount = if (segment != GraphSegment.TOTAL) {
    remember(segment) {
      segment.visibleLabelsCount()
    }
  } else {
    remember(state.minTarget, state.maxTarget) {
      getIntervalCount(
        startTimeStamp = state.minTarget ?: Calendar.getInstance().timeInMillis,
        endTimeStamp = state.maxTarget ?: Calendar.getInstance().timeInMillis,
      )
    }
  }
  val separators = GraphUtil.periodStarts(
    segment = segment,
    startMillis = if (timeStamps.isNotEmpty()) timeStamps.first() else null,
    endMillis = if (timeStamps.isNotEmpty()) timeStamps.last() else null,
  ).map { it.toDouble() }

  val primaryLayer = primaryLayer(
    segment = segment,
    yRangeValues = state.primaryYAxis,
    handleIntent = handleIntent,
  )

  // Secondary metrics are normalized to weight Y-axis range (iOS-style)
  // They use the same Y-axis as primary (weight)
  val secondaryLayer = secondaryLayer(
    segment = segment,
    yRangeValues = state.primaryYAxis, // Use primary Y-axis range (normalized values)
    handleIntent = handleIntent,
  )

  val fadingEdges = rememberFadingEdges(
    useVisiblePaddingWidth = true,
    visibilityThreshold = 24.dp,
    visibilityEasing = FastOutSlowInEasing,
  )

  val (visibleStartXStep, visibleEndXStep) = GraphSnapHelper.getVisiblePaddingXStepForSegment(segment)
  val primaryChart =
    rememberCartesianChart(
      primaryLayer,
      secondaryLayer,
      topAxis = topAxis(),
      startAxis = startAxis(segment, state.isSingleWindow),
      endAxis = endAxis(yStep = state.primaryYStep, isEmptyGraph = state.isEmptyGraph, markerDecoration = goalMarker),
      bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer),
      marker = emptyMarker(),
      persistentMarkers = if (markerIndex != null) {
        { defaultMarker at markerIndex }
      } else {
        null
      },
      fadingEdges = fadingEdges,
      visibleLabelsCount = visibleLabelsCount,
      getXStep = { GraphUtil.calculateXStep(segment) },
      layerPadding = {
        CartesianLayerPadding(
          visibleStartPaddingXStep = visibleStartXStep,
          visibleEndPaddingXStep = visibleEndXStep,
        )
      },
      onChartClick = onChartClick,
    )
  return primaryChart
}

fun getIntervalCount(startTimeStamp: Long, endTimeStamp: Long): Double {
  return GraphUtil.getTotalMonthsBetweenYears(
    startTimeStamp,
    endTimeStamp,
  ).toDouble().coerceAtLeast(1.0)
}
