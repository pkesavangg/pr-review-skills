package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.bottomAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.endAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.startAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.topAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.visibleLabelsCount
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.FadingEdges
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.rememberScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.ScrubMarkerController
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.util.Calendar

@Composable
fun rememberGraphChart(
  state: GraphState,
  defaultMarker: CartesianMarker,
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  fadingEdges: FadingEdges? = null,
  handleIntent: (GraphIntent) -> Unit,
  scrubController: ScrubMarkerController? = null,
): CartesianChart {
  // Get weightless mode from goal's account if available
  val isWeightlessOn = state.goal?.account?.isWeightlessOn ?: false
  val goalMarker = rememberGoalMarker(goal = state.goal, isWeightlessOn = isWeightlessOn)
  state.markerIndex
  val separators = GraphUtil.periodStarts(
    segment = segment,
    startMillis = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted().firstOrNull(),
    endMillis = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted().lastOrNull(),
  ).map { it.toDouble() }

  // Compute visible labels count per segment (same logic as v3)
  val visibleLabelsCount = if (segment != GraphSegment.TOTAL) {
    remember(segment) { segment.visibleLabelsCount() }
  } else {
    remember(state.minTarget, state.maxTarget) {
      GraphUtil.getTotalMonthsBetweenYears(
        state.minTarget ?: Calendar.getInstance().timeInMillis,
        state.maxTarget ?: Calendar.getInstance().timeInMillis,
      ).toDouble().coerceAtLeast(1.0)
    }
  }

  // ScrollAwareRangeProvider: single source of truth for Y-axis range + step
  val goalWeight = state.goal?.goalWeight ?: 0.0
  val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = state.chartMinX ?: Double.NaN,
    maxX = state.chartMaxX ?: Double.NaN,
  ) { visibleEntries ->
    if (visibleEntries.isEmpty()) {
      return@rememberScrollAwareRangeProvider (0.0..1.0) to emptyList()
    }
    val yValues = visibleEntries.map { it.second }
    val minY = yValues.min()
    val maxY = yValues.max()
    android.util.Log.d("ScrollAwareRange", "visibleEntries=${visibleEntries.size} minY=$minY maxY=$maxY")
    val axisMeta = generateNiceScale(
      minValue = minY,
      maxValue = maxY,
      goalWeight = goalWeight,
      isWeightLessMode = isWeightlessOn,
      targetTickCount = 4,
    )
    val rangeMinY = axisMeta.min
    val rangeMaxY = axisMeta.max
    val step = axisMeta.step
    // Dispatch nice-scale range + visible X range so normalization uses the exact same window
    val xValues = visibleEntries.map { it.first }
    handleIntent(GraphIntent.UpdateVisibleYRange(rangeMinY, rangeMaxY, xValues.min(), xValues.max()))
    // Generate tick labels from min to max at step intervals
    val ticks = mutableListOf<Double>()
    var tick = rangeMinY
    while (tick <= rangeMaxY + step * 0.01) {
      ticks.add(tick)
      tick += step
    }
    (rangeMinY..rangeMaxY) to ticks
  }

  val primaryLayer = primaryLayer(
    segment = segment,
    rangeProvider = scrollAwareRange,
    handleIntent = handleIntent,
  )

  val secondaryLayer = secondaryLayer(
    segment = segment,
    handleIntent = handleIntent,
  )

  val primaryChart =
    rememberCartesianChart(
      primaryLayer,
      secondaryLayer,
      topAxis = topAxis(),
      startAxis = startAxis(segment, state.isSingleWindow),
      endAxis = endAxis(
        isEmptyGraph = state.isEmptyGraph,
        markerDecoration = goalMarker,
        ticksProvider = { scrollAwareRange.currentTicks },
      ),
      bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer),
      marker = defaultMarker,
      fadingEdges = fadingEdges,
      getXStep = { GraphUtil.calculateXStep(segment) },
      markerController = scrubController ?: CartesianMarkerController.rememberShowOnPress(),
      visibleLabelsCount = visibleLabelsCount,
    )
  return primaryChart
}
