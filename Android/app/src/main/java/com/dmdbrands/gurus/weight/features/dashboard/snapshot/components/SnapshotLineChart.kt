package com.dmdbrands.gurus.weight.features.dashboard.snapshot.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphSnapHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlin.math.roundToInt

/**
 * Simplified static line chart for dashboard snapshot cards.
 * No scrolling, no markers, no snapping — just renders a line with axes.
 */
@Composable
fun SnapshotLineChart(
  modelProducer: CartesianChartModelProducer,
  lineColor: Color,
  lineColors: List<Color>? = null,
  secondaryLayerColor: Color? = null,
  startTimestamp: Long,
  endTimestamp: Long,
  yStep: Double? = null,
  yMin: Double? = null,
  yMax: Double? = null,
  modifier: Modifier = Modifier,
) {
  if (yMin == null || yMax == null) return

  val openSansFamily = FontFamily(Font(R.font.open_sans_semi_bold))
  val lineStyle = MeTheme.colorScheme.iconSecondaryDisabled
  val gridColor = MeTheme.colorScheme.utility
  val labelColor = MeTheme.colorScheme.textSubheading
  val stepValue = yStep?.takeIf { it > 0 } ?: 1.0
  val startPaddingXStep = GraphSnapHelper.getVisiblePaddingXStepForSegment(GraphSegment.WEEK).first

  val initialStartX = GraphUtil.getRollingWindowStart(GraphSegment.WEEK, endTimestamp)?.toDouble()
    ?: GraphUtil.getStartRange(GraphSegment.WEEK, endTimestamp)?.toDouble()
    ?: endTimestamp.toDouble()

  val initialScroll = remember(initialStartX, startPaddingXStep) {
    Scroll.Absolute.xWithPadding(initialStartX, startPaddingXStep)
  }

  val rangeProvider = remember(yMin, yMax, startTimestamp, endTimestamp) {
    CartesianLayerRangeProvider.fixed(
      minX = startTimestamp.toDouble(),
      maxX = endTimestamp.toDouble(),
      minY = yMin,
      maxY = yMax,
    )
  }

  val colors = lineColors ?: listOf(lineColor)

  val lines = colors.map { color ->
    LineCartesianLayer.rememberLine(
      fill = LineCartesianLayer.LineFill.single(Fill(color)),
      stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 3.dp),
      interpolator = LineCartesianLayer.Interpolator.monotone(),
      pointProvider = LineCartesianLayer.PointProvider.single(
        point = LineCartesianLayer.Point(
          component = rememberShapeComponent(
            Fill(color),
            CircleShape,
            strokeThickness = 0.dp,
          ),
          size = 8f.dp,
        ),
      ),
    )
  }

  val lineProvider = remember(lines) {
    LineCartesianLayer.LineProvider.series(lines)
  }

  val lineLayer = rememberLineCartesianLayer(
    lineProvider = lineProvider,
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = rangeProvider,
  )

  // Optional secondary layer (percentile bands — thin lines, no points)
  val secondaryLayer = if (secondaryLayerColor != null) {
    val secondaryLine = LineCartesianLayer.rememberLine(
      fill = LineCartesianLayer.LineFill.single(Fill(secondaryLayerColor.copy(alpha = 0.5f))),
      stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
      interpolator = LineCartesianLayer.Interpolator.cubic(1f),
    )
    rememberLineCartesianLayer(
      lineProvider = remember(secondaryLine) { LineCartesianLayer.LineProvider.series(listOf(secondaryLine)) },
    )
  } else null

  val layers = listOfNotNull(lineLayer, secondaryLayer)

  val chart = rememberCartesianChart(
    *layers.toTypedArray(),
    topAxis = HorizontalAxis.rememberTop(
      label = null,
      line = rememberAxisLineComponent(fill = Fill(lineStyle), thickness = 1.dp),
      guideline = null,
      tickLength = 0.dp,
    ),
    startAxis = VerticalAxis.rememberStart(
      label = null,
      line = rememberAxisLineComponent(fill = Fill(lineStyle), thickness = 1.dp),
      guideline = null,
      tick = null,
      size = BaseAxis.Size.Scroll(0.dp, isLabelsScrollable = true),
      tickLength = 0.dp,
    ),
    endAxis = VerticalAxis.rememberEnd(
      valueFormatter = CartesianValueFormatter { _, value, _ ->
        value.roundToInt().toString()
      },
      itemPlacer = VerticalAxis.ItemPlacer.step({ stepValue }),
      size = BaseAxis.Size.Fixed(40.dp),
      line = rememberAxisLineComponent(fill = Fill(lineStyle), thickness = 1.dp),
      guideline = rememberAxisLineComponent(fill = Fill(gridColor.copy(0.5f)), thickness = 1.dp),
      label = rememberTextComponent(
        style = TextStyle(
          fontFamily = openSansFamily,
          color = labelColor,
          fontSize = 14.sp,
        ),
      ),
      tickLength = 0.dp,
    ),
    bottomAxis = HorizontalAxis.rememberBottom(
      valueFormatter = CartesianValueFormatter { _, value, _ ->
        if (value.toLong() != 0L) {
          GraphUtil.formatTimestampForSegment(value.toLong(), GraphSegment.WEEK).lowercase()
        } else {
          " "
        }
      },
      itemPlacer = HorizontalAxis.ItemPlacer.aligned(),
      guideline = rememberAxisGuidelineComponent(fill = Fill(gridColor), thickness = 1.dp),
      label = rememberAxisLabelComponent(
        style = TextStyle(
          fontFamily = openSansFamily,
          color = labelColor,
          fontSize = 14.sp,
        ),
      ),
      tick = rememberAxisGuidelineComponent(),
      tickLength = 20.dp,
      labelVerticalMode = HorizontalAxis.LabelVerticalMode.Inside,
      labelHorizontalPosition = Position.Horizontal.End,
      line = rememberAxisLineComponent(fill = Fill(lineStyle), thickness = 1.dp),
    ),
    visibleLabelsCount = 7.0,
    getXStep = { GraphUtil.calculateXStep(GraphSegment.WEEK) },
  )

  CartesianChartHost(
    chart = chart,
    modelProducer = modelProducer,
    modifier = modifier
      .fillMaxWidth()
      .height(200.dp),
    animateIn = false,
    scrollState = rememberVicoScrollState(
      scrollEnabled = false,
      initialScroll = initialScroll,
    ),
    zoomState = rememberVicoZoomState(zoomEnabled = false),
  )
}
