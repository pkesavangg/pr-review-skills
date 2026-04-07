package com.dmdbrands.gurus.weight.features.common.components.chart.bp

import android.annotation.SuppressLint
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.FadingEdges
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.rememberScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.ScrubMarkerController
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.util.Calendar

/** BP line colors: systolic (green), diastolic (darker gray-green), pulse (blue). */
private object BpChartColors {
  val Systolic = SnapshotColors.BloodPressure   // #458239
  val Diastolic = Color(0xFF7B726E)              // gray/subheading
  val Pulse = Color(0xFF00B3E3)                  // pulse blue
}

@SuppressLint("RestrictedApi")
@Composable
fun rememberBpChart(
  state: GraphState,
  defaultMarker: CartesianMarker,
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  fadingEdges: FadingEdges? = null,
  handleIntent: (GraphIntent) -> Unit,
  scrubController: ScrubMarkerController? = null,
): CartesianChart {
  state.markerIndex
  val separators = GraphUtil.periodStarts(
    segment = segment,
    startMillis = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted().firstOrNull(),
    endMillis = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted().lastOrNull(),
  ).map { it.toDouble() }

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

  val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = state.chartMinX ?: Double.NaN,
    maxX = state.chartMaxX ?: Double.NaN,
  ) { visibleEntries, visibleXRange ->
    if (visibleEntries.isEmpty()) {
      return@rememberScrollAwareRangeProvider (0.0..1.0) to emptyList()
    }
    val relativeMin = GraphUtil.getRelativeStart(segment, visibleXRange.start.toLong())
    val relativeMax = GraphUtil.getRelativeEnd(segment, visibleXRange.endInclusive.toLong())
    val clipRange = GraphUtil.clipRangeForGraph(segment, relativeMin, relativeMax)

    val yValues = visibleEntries.map { it.second }
    val axisMeta = generateNiceScale(
      minValue = yValues.min(),
      maxValue = yValues.max(),
      goalWeight = 0.0,
      targetTickCount = 4,
    )
    val rangeMinY = axisMeta.min
    val rangeMaxY = axisMeta.max
    val step = axisMeta.step

    handleIntent(
      GraphIntent.UpdateVisibleYRange(
        rangeMinY, rangeMaxY,
        clipRange.startMillis.toDouble(), clipRange.endMillis.toDouble(),
      ),
    )
    val ticks = mutableListOf<Double>()
    var tick = rangeMinY
    while (tick <= rangeMaxY + step * 0.01) {
      ticks.add(tick)
      tick += step
    }
    (rangeMinY..rangeMaxY) to ticks
  }

  // 3-line layer: systolic, diastolic, pulse — all in one layer sharing Y axis
  val lineThickness = if (segment == GraphSegment.TOTAL) 2.dp else 3.dp
  val pointSize = if (segment == GraphSegment.TOTAL) 5f else 8f

  val colors = listOf(BpChartColors.Systolic, BpChartColors.Diastolic, BpChartColors.Pulse)
  val lines = colors.map { color ->
    LineCartesianLayer.rememberLine(
      fill = LineCartesianLayer.LineFill.single(Fill(color)),
      stroke = LineCartesianLayer.LineStroke.Continuous(thickness = lineThickness),
      interpolator = LineCartesianLayer.Interpolator.monotone(),
      pointProvider = LineCartesianLayer.PointProvider.single(
        point = LineCartesianLayer.Point(
          component = rememberShapeComponent(Fill(color), CircleShape, strokeThickness = 0.dp),
          size = pointSize.dp,
        ),
      ),
    )
  }
  val lineProvider = remember(lines) { LineCartesianLayer.LineProvider.series(lines) }
  val bpLayer = rememberLineCartesianLayer(
    lineProvider = lineProvider,
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = scrollAwareRange,
  )

  return rememberCartesianChart(
    bpLayer,
    topAxis = topAxis(),
    startAxis = startAxis(segment, state.isSingleWindow),
    endAxis = endAxis(
      isEmptyGraph = state.isEmptyGraph,
      ticksProvider = { scrollAwareRange.currentTicks },
    ),
    bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer),
    marker = defaultMarker,
    fadingEdges = fadingEdges,
    getXStep = { GraphUtil.calculateXStep(segment) },
    markerController = scrubController ?: CartesianMarkerController.rememberShowOnPress(),
    visibleLabelsCount = visibleLabelsCount,
  )
}
