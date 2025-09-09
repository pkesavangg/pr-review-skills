package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
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
import java.util.Calendar

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
  initialTimeStamp: Long? = null
): LineCartesianLayer {

  // Animated values for smooth transitions with null safety
  val animatedMinTarget: Int? = minYTarget?.let { target ->
    val value by animateIntAsState(
      targetValue = target,
      animationSpec = tween(300),
      label = "minTarget",
    )
    value
  }

  val animatedMaxTarget: Int? = maxYTarget?.let { target ->
    val value by animateIntAsState(
      targetValue = target,
      animationSpec = tween(300),
      label = "maxTarget",
    )
    value
  }

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

  val todayMills = Calendar.getInstance().timeInMillis

  val startRangeX = initialTimeStamp?.let { GraphUtil.getStartRange(segment, it) }
  val endRangeX = GraphUtil.getEndRange(segment, todayMills)

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
    rangeProvider =
      object : CartesianLayerRangeProvider {
        override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
          return animatedMinTarget?.toDouble() ?: super.getMinY(minY, maxY, extraStore)
        }

        override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
          return animatedMaxTarget?.toDouble() ?: super.getMaxY(minY, maxY, extraStore)
        }

        override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
          return endRangeX.toDouble()
        }

        override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
          return startRangeX?.toDouble() ?: super.getMinX(minX, maxX, extraStore)
        }
      },
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
  initialTimeStamp: Long
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.primaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End,
    minYTarget = minYTarget,
    maxYTarget = maxYTarget,
    initialTimeStamp = initialTimeStamp,
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
  initialTimeStamp: Long? = null
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.secondaryAction,
    verticalAxisPosition = Axis.Position.Vertical.Start,
    minYTarget = minYTarget,
    maxYTarget = maxYTarget,
    initialTimeStamp = initialTimeStamp,
  )
}
