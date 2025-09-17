package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineWithConnectionCondition
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/**
 * Common composable for creating line layers with connection condition.
 * Used for both primary and secondary layers with configurable color and axis position.
 */
@Composable
internal fun rememberLineLayerWithConnection(
  segment: GraphSegment,
  lineColor: Color,
  verticalAxisPosition: Axis.Position.Vertical,
  yRangeValues: CartesianRangeValues?,
): LineCartesianLayer {

  val minYTarget = yRangeValues?.minY
  val maxYTarget = yRangeValues?.maxY

  val connectionCondition: (Long, Long?) -> Boolean = { minXTarget, maxXTarget ->
    if (maxXTarget == null) {
      false
    } else {
      val diffInDays = (maxXTarget - minXTarget) / (1000 * 60 * 60 * 24)
      val disconnectionCriteria = when (segment) {
        GraphSegment.WEEK -> diffInDays < 7
        GraphSegment.MONTH -> diffInDays < 30
        GraphSegment.YEAR -> diffInDays < 365
        else -> true
      }

      disconnectionCriteria
    }
  }


  return rememberLineCartesianLayer(
    lineProvider = LineCartesianLayer.LineProvider.series(
      listOf(lineColor).map {
        LineCartesianLayer.rememberLineWithConnectionCondition(
          fill = LineCartesianLayer.LineFill.single(fill(it)),
          stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.dp),
          pointConnector = LineCartesianLayer.PointConnector.cubic(0.5f),
          pointProvider = LineCartesianLayer.PointProvider.single(
            point = LineCartesianLayer.Point(
              rememberShapeComponent(
                fill(it),
                CorneredShape.Pill,
                strokeThickness = 0.dp,
              ),
            ),
          ),
          connectionCondition = { minXTarget, maxXTarget ->
            connectionCondition(minXTarget.x.toLong(), maxXTarget?.x?.toLong())
          },
        )
      },
    ),
    verticalAxisPosition = verticalAxisPosition,
    rangeProvider = CartesianLayerRangeProvider.fixed(
      maxY = maxYTarget?.toDouble(),
      minY = minYTarget?.toDouble(),
    ),
  )
}

/**
 * Convenience function for creating primary layer.
 */
@Composable
internal fun primaryLayer(
  segment: GraphSegment,
  yRangeValues: CartesianRangeValues? = null
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.primaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End,
    yRangeValues = yRangeValues,
  )
}

/**
 * Convenience function for creating secondary layer.
 */
@Composable
internal fun secondaryLayer(
  segment: GraphSegment,
  yRangeValues: CartesianRangeValues? = null
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.secondaryAction,
    verticalAxisPosition = Axis.Position.Vertical.Start,
    yRangeValues = yRangeValues,
  )
}
