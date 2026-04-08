package com.dmdbrands.gurus.weight.features.common.components.chart

import android.annotation.SuppressLint
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.bottomAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.endAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.startAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.topAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.config.ChartConfig
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.ProductGraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.visibleLabelsCount
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

/**
 * Unified chart builder driven by [ChartConfig].
 * Replaces per-product chart builders (WeightChart, BpChart).
 * Differences between products are expressed declaratively via config.
 */
@SuppressLint("RestrictedApi")
@Composable
fun rememberProductChart(
  config: ChartConfig,
  graphState: GraphState,
  productState: ProductGraphState,
  defaultMarker: CartesianMarker,
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  fadingEdges: FadingEdges? = null,
  handleIntent: (GraphIntent) -> Unit,
  scrubController: ScrubMarkerController? = null,
): CartesianChart {
  productState.markerIndex

  // ── Separators (shared) ──
  val separators = GraphUtil.periodStarts(
    segment = segment,
    startMillis = productState.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted().firstOrNull(),
    endMillis = productState.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted().lastOrNull(),
  ).map { it.toDouble() }

  // ── Visible labels count (shared) ──
  val visibleLabelsCount = if (segment != GraphSegment.TOTAL) {
    remember(segment) { segment.visibleLabelsCount() }
  } else {
    remember(productState.minTarget, productState.maxTarget) {
      GraphUtil.getTotalMonthsBetweenYears(
        productState.minTarget ?: Calendar.getInstance().timeInMillis,
        productState.maxTarget ?: Calendar.getInstance().timeInMillis,
      ).toDouble().coerceAtLeast(1.0)
    }
  }

  // ── Y-range provider (config-driven) ──
  val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = productState.chartMinX ?: Double.NaN,
    maxX = productState.chartMaxX ?: Double.NaN,
  ) { visibleSeriesEntries, visibleXRange ->
    // Extract Y values: all series (BP) or first series only (weight/baby)
    val yValues = if (config.useAllSeriesForYRange) {
      visibleSeriesEntries.flatMap { series -> series.map { it.second } }
    } else {
      visibleSeriesEntries.firstOrNull()?.map { it.second } ?: emptyList()
    }
    if (yValues.isEmpty()) {
      return@rememberScrollAwareRangeProvider (0.0..1.0) to emptyList()
    }

    val relativeMin = GraphUtil.getRelativeStart(segment, visibleXRange.start.toLong())
    val relativeMax = GraphUtil.getRelativeEnd(segment, visibleXRange.endInclusive.toLong())
    val clipRange = GraphUtil.clipRangeForGraph(segment, relativeMin, relativeMax)

    val axisMeta = generateNiceScale(
      minValue = yValues.min(),
      maxValue = yValues.max(),
      goalWeight = config.goalWeight ?: 0.0,
      isWeightLessMode = config.isWeightlessMode,
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

  // ── Line layers (config-driven) ──
  val lineThickness = if (segment == GraphSegment.TOTAL) 2.dp else 3.dp
  val pointSize = if (segment == GraphSegment.TOTAL) 5f else 8f

  val lines = config.lines.map { spec ->
    LineCartesianLayer.rememberLine(
      fill = LineCartesianLayer.LineFill.single(Fill(spec.color)),
      stroke = LineCartesianLayer.LineStroke.Continuous(thickness = lineThickness),
      interpolator = LineCartesianLayer.Interpolator.monotone(),
      pointProvider = LineCartesianLayer.PointProvider.single(
        point = LineCartesianLayer.Point(
          component = rememberShapeComponent(Fill(spec.color), CircleShape, strokeThickness = 0.dp),
          size = pointSize.dp,
        ),
      ),
    )
  }
  val lineProvider = remember(lines) { LineCartesianLayer.LineProvider.series(lines) }
  val primaryLayer = rememberLineCartesianLayer(
    lineProvider = lineProvider,
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = scrollAwareRange,
  )

  // ── Optional secondary layer (weight metric overlay) ──
  val layers = if (config.hasSecondaryLayer && config.secondaryLineColor != null) {
    val secLayer = secondaryLayer(
      segment = segment,
      rangeProvider = scrollAwareRange,
      handleIntent = handleIntent,
      yTransform = { series, yRange, visibleXRange ->
        GraphUtil.normalizeYValues(
          series = series,
          weightMin = yRange.minY,
          weightMax = yRange.maxY,
          minX = visibleXRange.start.toLong(),
          maxX = visibleXRange.endInclusive.toLong(),
        )
      },
    )
    arrayOf(primaryLayer, secLayer)
  } else {
    arrayOf(primaryLayer)
  }

  // ── Goal marker (config-driven) ──
  val goalMarker = if (config.goalWeight != null) {
    rememberGoalMarker(goal = graphState.goal, isWeightlessOn = config.isWeightlessMode)
  } else null

  // ── Build chart ──
  return rememberCartesianChart(
    *layers,
    topAxis = topAxis(),
    startAxis = startAxis(segment, productState.isSingleWindow),
    endAxis = endAxis(
      isEmptyGraph = productState.isEmptyGraph,
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
}
