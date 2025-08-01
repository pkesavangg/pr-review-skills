package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
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
import java.util.Calendar

/**
 * Internal helper to remember the primary layer for the graph.
 */
@Composable
internal fun primaryLayer(
  segment: GraphSegment,
  minYTarget: Int,
  maxYTarget: Int,
  initialTimeStamp: Long
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

  val todayMills = Calendar.getInstance().timeInMillis

  val startRangeX = when (segment) {
    GraphSegment.WEEK -> DateTimeConverter.getWeekRange(initialTimeStamp).start
    GraphSegment.MONTH -> DateTimeConverter.getMonthRange(initialTimeStamp).start
    GraphSegment.YEAR -> DateTimeConverter.getYearRange(initialTimeStamp).start
    else -> null
  }

  val endRangeX = when (segment) {
    GraphSegment.WEEK -> DateTimeConverter.getWeekRange(todayMills).end
    GraphSegment.MONTH -> DateTimeConverter.getMonthRange(todayMills).end
    GraphSegment.YEAR -> DateTimeConverter.getYearRange(todayMills).end
    else -> null
  }

  return rememberLineCartesianLayer(
    lineProvider = LineCartesianLayer.LineProvider.series(
      listOf(MeTheme.colorScheme.primaryAction).map {
        LineCartesianLayer.rememberLineWithConnectionCondition(
          fill = LineCartesianLayer.LineFill.single(fill(it)),
          stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.dp),
          pointConnector = LineCartesianLayer.PointConnector.cubic(0.5f),
          pointProvider = LineCartesianLayer.PointProvider.single(
            point = LineCartesianLayer.Point(
              rememberShapeComponent(
                fill(it),
                CorneredShape.Pill,
                strokeThickness = 2.dp,
              ),
            ),
          ),
          connectionCondition = { minXTarget, maxXTarget ->
            connectionCondition(minXTarget.x.toLong(), maxXTarget?.x?.toLong())
          },
        )
      },
    ),
    verticalAxisPosition = Axis.Position.Vertical.End,
    rangeProvider = object : CartesianLayerRangeProvider {
      override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        return minYTarget.toDouble()
      }

      override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        return maxYTarget.toDouble()
      }

      override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
        return endRangeX?.toDouble() ?: super.getMaxX(minX, maxX, extraStore)
      }

      override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double {
        return startRangeX?.toDouble() ?: super.getMinX(minX, maxX, extraStore)
      }
    },
    pointSpacing = pointSpacing(segment, 10.dp),
  )
}

/**
 * Calculates the point spacing for the graph based on the segment and axis padding.
 * UI-only logic, not business/data transformation.
 */
@Composable
fun pointSpacing(
  segment: GraphSegment,
  axisPadding: Dp = 0.dp
): Dp {
  val windowInfo = LocalWindowInfo.current
  val screenWidthPx = windowInfo.containerSize.width
  val density = LocalDensity.current
  val intervalCount = remember(segment) {
    when (segment) {
      GraphSegment.WEEK -> 7
      GraphSegment.MONTH -> 6
      GraphSegment.YEAR -> 12
      else -> 32
    }
  }
  return remember(segment, screenWidthPx, intervalCount, axisPadding) {
    with(density) { (screenWidthPx / intervalCount).toDp() - axisPadding }
  }
}
