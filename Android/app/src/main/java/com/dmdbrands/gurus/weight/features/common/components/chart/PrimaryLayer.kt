package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import android.annotation.SuppressLint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
  yRangeValues: CartesianRangeValues?,
): LineCartesianLayer {

  val minYTarget = yRangeValues?.minY
  val maxYTarget = yRangeValues?.maxY

  // Track current values for animation duration calculation
  val currentMinY = remember { mutableStateOf(minYTarget?.toFloat() ?: 0f) }
  val currentMaxY = remember { mutableStateOf(maxYTarget?.toFloat() ?: 0f) }

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

  // Calculate shared duration based on the larger difference between min and max
  val minDifference = if (minYTarget != null) abs(minYTarget - currentMinY.value) else 0.0
  val maxDifference = if (maxYTarget != null) abs(maxYTarget - currentMaxY.value) else 0.0
  val maxDifferenceValue = max(minDifference, maxDifference)

  val sharedDuration = when {
    maxDifferenceValue < 19 -> 300 // Short duration for small differences
    maxDifferenceValue < 20 -> 500 // Medium duration for medium differences
    else -> 700 // Long duration for large differences (>= 20)
  }

  val animatedMinY = if (minYTarget != null) {
    animateFloatAsState(
      targetValue = minYTarget.toFloat(),
      animationSpec = tween(durationMillis = sharedDuration),
    ).also {
      currentMinY.value = it.value
    }
  } else null

  val animatedMaxY = if (maxYTarget != null) {
    animateFloatAsState(
      targetValue = maxYTarget.toFloat(),
      animationSpec = tween(durationMillis = sharedDuration),
    ).also {
      currentMaxY.value = it.value
    }
  } else null


  return rememberLineCartesianLayer(
    lineProvider = LineCartesianLayer.LineProvider.series(
      listOf(lineColor).map {
        LineCartesianLayer.rememberLine(
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
        )
      },
    ),
    verticalAxisPosition = verticalAxisPosition,
    rangeProvider = CartesianLayerRangeProvider.fixed(
      maxY = animatedMaxY?.value?.toDouble(),
      minY = animatedMinY?.value?.toDouble(),
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
