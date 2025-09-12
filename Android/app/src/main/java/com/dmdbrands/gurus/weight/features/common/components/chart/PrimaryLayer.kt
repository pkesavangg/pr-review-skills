package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
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
  minYTarget: Int?,
  maxYTarget: Int?,
  startRangeX: Long?,
  endRangeX: Long?,
): LineCartesianLayer {

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

  // Memoize the range provider to prevent unnecessary recomposition
  val rangeProvider = remember(minYTarget, maxYTarget) {
    object : CartesianLayerRangeProvider {
      override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        return minYTarget?.toDouble() ?: super.getMinY(minY, maxY, extraStore)
      }

      override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        return maxYTarget?.toDouble() ?: super.getMaxY(minY, maxY, extraStore)
      }

      override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
        return startRangeX?.toDouble() ?: super.getMinX(minX, maxX, extraStore)
      }

      override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
        return endRangeX?.toDouble() ?: super.getMaxX(minX, maxX, extraStore)
      }
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
    rangeProvider = rangeProvider,
  )
}

/**
 * Convenience function for creating primary layer.
 */
@Composable
internal fun primaryLayer(
  segment: GraphSegment,
  minYTarget: Int?,
  maxYTarget: Int?,
  startRangeX: Long?,
  endRangeX: Long?,
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.primaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End,
    minYTarget = minYTarget,
    maxYTarget = maxYTarget,
    startRangeX = startRangeX,
    endRangeX = endRangeX,
  )
}

/**
 * Convenience function for creating secondary layer.
 */
@Composable
internal fun secondaryLayer(
  segment: GraphSegment,
  minYTarget: Int?,
  maxYTarget: Int?,
  startRangeX: Long?,
  endRangeX: Long?,
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.secondaryAction,
    verticalAxisPosition = Axis.Position.Vertical.Start,
    minYTarget = minYTarget,
    maxYTarget = maxYTarget,
    startRangeX = startRangeX,
    endRangeX = endRangeX,
  )
}
