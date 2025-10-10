package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.bottomAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.endAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.startAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.topAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker

@Composable
fun rememberGraphChart(
  state: GraphState,
  defaultMarker: CartesianMarker,
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  onChartClick: ((List<Double>, Double?) -> Unit)? = null,
  handleIntent: (GraphIntent) -> Unit,
): CartesianChart {
  val goalMarker = rememberGoalMarker(goal = state.goal)
  val markerIndex = state.markerIndex
  val timeStamps = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted()
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

  val secondaryLayer = secondaryLayer(
    segment = segment,
    yRangeValues = state.secondaryYAxis,
    handleIntent = handleIntent,
  )

  val primaryChart =
    rememberCartesianChart(
      primaryLayer,
      secondaryLayer,
      startAxis = startAxis(),
      topAxis = topAxis(),
      endAxis = endAxis(yStep = state.primaryYStep, isEmptyGraph = state.isEmptyGraph, markerDecoration = goalMarker),
      bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer),
      marker = emptyMarker(),
      persistentMarkers = if (markerIndex != null) {
        { defaultMarker at markerIndex }
      } else {
        null
      },
      visibleLabelsCount = state.getIntervalCount(segment = segment),
      getXStep = { GraphUtil.calculateXStep(segment) },
      onChartClick = onChartClick,
    )
  return primaryChart
}

// @Composable
// fun updateWeightLabel(
//   state: GraphState,
//   scrollState : VicoScrollState,
//   min: Long? = null,
//   max: Long? = null,
// ) {
//   val scope = rememberCoroutineScope()
//   val minTarget = min ?: state.minTarget ?: 0L
//   val maxTarget = max ?: state.maxTarget ?: 0L
//   scope.launch {
//     val validWeightLabel = calculateWeightLabel(state.graphLines, minTarget, maxTarget)
//
//     val weightLabel: String = validWeightLabel ?: run {
//       // Get visible X-axis labels using the provided item placer
//       val visibleXLabels = scrollState.getVisibleAxisLabels(
//         itemPlacer = horizontalItemPlacer,
//       )
//
//       // Interpolate Y-values for the visible X-axis range using cubic interpolation
//       val interpolatedYValues = scrollState.getInterpolatedYValues(
//         xValues = visibleXLabels,
//         interpolationType = InterpolationType.CUBIC,
//       )
//
//       // Take the first set of interpolated values (if available)
//       val firstYValues = interpolatedYValues.firstOrNull()
//
//       // Compute the formatted average weight value or return a blank string
//       firstYValues?.toList()
//         ?.map { it.toDouble() }
//         ?.average()
//         ?.let { String.format(Locale.US, "%.1f", it) }
//         ?: " "
//     }
//
//     onWeightLabelChange(weightLabel)
//   }
// }
