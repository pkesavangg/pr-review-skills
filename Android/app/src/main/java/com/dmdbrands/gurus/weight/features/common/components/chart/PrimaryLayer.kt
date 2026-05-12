package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import android.annotation.SuppressLint

/**
 * Common composable for creating line layers with connection condition.
 * Used for both primary and secondary layers with configurable color and axis position.
 */
@SuppressLint("RestrictedApi")
@Composable
internal fun rememberLineLayerWithConnection(
  segment: GraphSegment,
  lineColor: Color,
  verticalAxisPosition: Axis.Position.Vertical,
  rangeProvider: CartesianLayerRangeProvider = remember { CartesianLayerRangeProvider.auto() },
  yTransform: ((
    series: List<com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel.Entry>,
    yRange: com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
  alwaysUseLiveRange: Boolean = false,
): LineCartesianLayer {

  val lineThickness = if (segment == GraphSegment.TOTAL) 2.dp else 3.dp
  val pointSize = if (segment == GraphSegment.TOTAL) 5f else 8f

  // Create line instance during composition (rememberLine is already memoized internally)
  val line = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = lineThickness),
    interpolator = LineCartesianLayer.Interpolator.monotone(),
    pointProvider = LineCartesianLayer.PointProvider.single(
      point = LineCartesianLayer.Point(
        component = rememberShapeComponent(
          Fill(lineColor),
          CircleShape,
          strokeThickness = 0.dp,
        ),
        size = pointSize.dp,
      ),
    ),
  )

  // Memoize lineProvider wrapper to prevent unnecessary recreations
  val lineProvider = remember(line) {
    LineCartesianLayer.LineProvider.series(listOf(line))
  }

  return rememberLineCartesianLayer(
    lineProvider = lineProvider,
    verticalAxisPosition = verticalAxisPosition,
    rangeProvider = rangeProvider,
    yTransform = yTransform,
    alwaysUseLiveRange = alwaysUseLiveRange,
  )
}

/**
 * Convenience function for creating primary layer.
 */
@Composable
internal fun primaryLayer(
  segment: GraphSegment,
  rangeProvider: CartesianLayerRangeProvider = remember { CartesianLayerRangeProvider.auto() },
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.primaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = rangeProvider,
  )
}

/**
 * Convenience function for creating secondary layer.
 * Secondary metrics use their own range provider for independent Y-axis scaling.
 */
@Composable
internal fun secondaryLayer(
  segment: GraphSegment,
  rangeProvider: CartesianLayerRangeProvider = remember { CartesianLayerRangeProvider.auto() },
  yTransform: ((
    series: List<com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel.Entry>,
    yRange: com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.secondaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = rangeProvider,
    yTransform = yTransform,
    alwaysUseLiveRange = true,
  )
}
