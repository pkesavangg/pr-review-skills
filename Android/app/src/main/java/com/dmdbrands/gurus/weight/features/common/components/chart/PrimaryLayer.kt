package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
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
import kotlin.math.abs
import kotlin.math.max
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
  yRangeValues: CartesianRangeValues?,
  handleIntent: (GraphIntent) -> Unit
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
  max(minDifference, maxDifference)

  val sharedDuration = 100

  LaunchedEffect(maxYTarget) {
    handleIntent(GraphIntent.UpdateIsUpdating(true))
  }

  if (minYTarget != null) {
    animateFloatAsState(
      targetValue = minYTarget.toFloat(),
      animationSpec = tween(durationMillis = sharedDuration),
    ).also {
      currentMinY.value = it.value
    }
  } else null

  if (maxYTarget != null) {
    animateFloatAsState(
      targetValue = maxYTarget.toFloat(),
      animationSpec = tween(durationMillis = sharedDuration),
    ).also {
      currentMaxY.value = it.value
    }
  } else null

  val lineThickness = if (segment == GraphSegment.TOTAL) 2.5.dp else 3.dp
  val pointSize = if (segment == GraphSegment.TOTAL) 7f else 8f


  return rememberLineCartesianLayer(
    lineProvider = LineCartesianLayer.LineProvider.series(
      listOf(lineColor).map {
        LineCartesianLayer.rememberLine(
          fill = LineCartesianLayer.LineFill.single(fill(it)),
          stroke = LineCartesianLayer.LineStroke.continuous(thickness = lineThickness),
          pointConnector = LineCartesianLayer.PointConnector.cubic(0.5f),
          pointProvider = LineCartesianLayer.PointProvider.single(
            point = LineCartesianLayer.Point(
              component = rememberShapeComponent(
                fill(it),
                CorneredShape.Pill,
                strokeThickness = 0.dp,
              ),
              sizeDp = pointSize,
            ),
          ),
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
  yRangeValues: CartesianRangeValues? = null,
  handleIntent: (GraphIntent) -> Unit
): LineCartesianLayer {
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.primaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End,
    yRangeValues = yRangeValues,
    handleIntent = handleIntent,
  )
}

/**
 * Convenience function for creating secondary layer.
 * Secondary metrics are normalized to weight Y-axis range (iOS-style),
 * so they use the same Y-axis as primary (weight).
 */
@Composable
internal fun secondaryLayer(
  segment: GraphSegment,
  yRangeValues: CartesianRangeValues? = null,
  handleIntent: (GraphIntent) -> Unit
): LineCartesianLayer {
  // Secondary metrics now use the same Y-axis as primary (normalized values)
  // Use End position to share the same axis as primary
  return rememberLineLayerWithConnection(
    segment = segment,
    lineColor = MeTheme.colorScheme.secondaryAction,
    verticalAxisPosition = Axis.Position.Vertical.End, // Same axis as primary
    yRangeValues = yRangeValues, // Same range as primary (weight)
    handleIntent = handleIntent,
  )
}
