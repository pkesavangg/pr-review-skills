package com.dmdbrands.gurus.weight.features.common.components.chart

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

internal typealias LineYTransform = (
  series: List<LineCartesianLayerModel.Entry>,
  yRange: CartesianChartRanges.YRange,
  visibleXRange: ClosedFloatingPointRange<Double>,
) -> DoubleArray?

/**
 * Builds a line layer driven by [rangeProvider]. When paired with [yTransform] +
 * [alwaysUseLiveRange] = true, vico invokes the transform on series-change / animation-target
 * change and projects cached output against the live yRange — used for a secondary line that
 * shares the primary's animated Y range without per-frame recomputation.
 */
@SuppressLint("RestrictedApi")
@Composable
private fun lineLayer(
  segment: GraphSegment,
  lineColor: Color,
  rangeProvider: CartesianLayerRangeProvider,
  yTransform: LineYTransform? = null,
  alwaysUseLiveRange: Boolean = false,
): LineCartesianLayer {
  val lineThickness = if (segment == GraphSegment.TOTAL) 2.dp else 3.dp
  val pointSize = if (segment == GraphSegment.TOTAL) 5f else 8f

  val line = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
    stroke = LineCartesianLayer.LineStroke.continuous(thickness = lineThickness),
    pointConnector = LineCartesianLayer.PointConnector.monotone(),
    pointProvider = LineCartesianLayer.PointProvider.single(
      point = LineCartesianLayer.Point(
        component = rememberShapeComponent(
          fill(lineColor),
          CorneredShape.Pill,
          strokeThickness = 0.dp,
        ),
        sizeDp = pointSize,
      ),
    ),
  )

  val lineProvider = remember(line) {
    LineCartesianLayer.LineProvider.series(listOf(line))
  }

  return rememberLineCartesianLayer(
    lineProvider = lineProvider,
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = rangeProvider,
    yTransform = yTransform,
    alwaysUseLiveRange = alwaysUseLiveRange,
  )
}

@Composable
internal fun primaryLayer(
  segment: GraphSegment,
  rangeProvider: CartesianLayerRangeProvider,
): LineCartesianLayer = lineLayer(
  segment = segment,
  lineColor = MeTheme.colorScheme.primaryAction,
  rangeProvider = rangeProvider,
)

@Composable
internal fun secondaryLayer(
  segment: GraphSegment,
  rangeProvider: CartesianLayerRangeProvider,
  yTransform: LineYTransform,
): LineCartesianLayer = lineLayer(
  segment = segment,
  lineColor = MeTheme.colorScheme.secondaryAction,
  rangeProvider = rangeProvider,
  yTransform = yTransform,
  alwaysUseLiveRange = true,
)
