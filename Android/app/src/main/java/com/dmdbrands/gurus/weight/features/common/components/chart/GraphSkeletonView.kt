package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.power.powerSaveAwareInfiniteFloat
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Skeleton loader matching iOS GraphSkeletonView.swift exactly.
 * Grid lines drawn via a single Canvas (1 layout pass vs 13 Spacer+Box views).
 */
@Composable
fun GraphSkeletonView(
  modifier: Modifier = Modifier,
) {
  // Rests at a mid-shimmer value (static dim placeholder) under Power Saving Mode (MOB-226).
  val animPhase = powerSaveAwareInfiniteFloat(
    label = "graphSkeletonAlpha",
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = androidx.compose.animation.core.EaseInOut),
      repeatMode = RepeatMode.Reverse,
    ),
    restingValue = 0.5f,
  )

  val skeletonAlpha = 0.2f + animPhase * 0.2f
  val gridLineAlpha = 0.08f + animPhase * 0.07f
  val skeletonColor = MeTheme.colorScheme.textSubheading

  val yAxisTickCount = 5
  val xAxisTickCount = 8

  // VStack(alignment: .leading, spacing: 0)
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.Start,
  ) {
    // Graph area with grid
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(start = MeTheme.spacing.xs),
    ) {
      // Chart area — ZStack with grid lines + wavy line
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxSize()
          .padding(end = 8.dp),
      ) {
        SkeletonGrid(
          gridColor = skeletonColor.copy(alpha = gridLineAlpha),
          yAxisTickCount = yAxisTickCount,
          xAxisTickCount = xAxisTickCount,
        )
        SkeletonChartLine(color = skeletonColor.copy(alpha = skeletonAlpha))
      }

      // Y-axis labels on the right — VStack(spacing: 0), .frame(width: 30)
      SkeletonYAxisLabels(
        color = skeletonColor.copy(alpha = skeletonAlpha),
        tickCount = yAxisTickCount,
      )
    }

    // X-axis labels at the bottom — HStack(spacing: 0)
    SkeletonXAxisLabels(
      color = skeletonColor.copy(alpha = skeletonAlpha),
      tickCount = xAxisTickCount,
    )
  }
}

/** Grid lines — single Canvas draws all horizontal + vertical lines. */
@Composable
private fun SkeletonGrid(
  gridColor: Color,
  yAxisTickCount: Int,
  xAxisTickCount: Int,
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val lineWidth = 1.dp.toPx()
    // Horizontal grid lines (yAxisTickCount lines from top to bottom edge)
    for (i in 0 until yAxisTickCount) {
      val y = size.height * i / (yAxisTickCount - 1).toFloat()
      drawLine(gridColor, Offset(0f, y), Offset(size.width, y), lineWidth)
    }
    // Vertical grid lines (xAxisTickCount lines from left to right edge)
    for (i in 0 until xAxisTickCount) {
      val x = size.width * i / (xAxisTickCount - 1).toFloat()
      drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), lineWidth)
    }
  }
}

/** Simulated chart line — .padding(.vertical, 20). */
@Composable
private fun SkeletonChartLine(color: Color) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 20.dp)
      .drawWithContent {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val path = Path().apply {
          moveTo(0f, midY + h * 0.2f)
          val points = listOf(
            0.15f to 0.15f,
            0.30f to 0.10f,
            0.45f to 0.05f,
            0.60f to -0.05f,
            0.75f to -0.10f,
            0.90f to -0.15f,
            1.00f to -0.20f,
          )
          for ((xRatio, yRatio) in points) {
            lineTo(w * xRatio, midY + h * yRatio)
          }
        }
        drawPath(
          path = path,
          color = color,
          style = Stroke(width = 3.dp.toPx()),
        )
      },
  )
}

@Composable
private fun SkeletonYAxisLabels(color: Color, tickCount: Int) {
  Column(
    modifier = Modifier
      .width(30.dp)
      .fillMaxSize(),
  ) {
    for (i in 0 until tickCount) {
      if (i > 0) Spacer(modifier = Modifier.weight(1f))
      SkeletonLabelBox(color)
    }
  }
}

@Composable
private fun SkeletonXAxisLabels(color: Color, tickCount: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = MeTheme.spacing.xs, end = 38.dp, top = 8.dp),
  ) {
    for (i in 0 until tickCount) {
      if (i > 0) Spacer(modifier = Modifier.weight(1f))
      SkeletonLabelBox(color)
    }
  }
}

@Composable
private fun SkeletonLabelBox(color: Color) {
  Box(
    modifier = Modifier
      .width(24.dp)
      .height(12.dp)
      .clip(RoundedCornerShape(2.dp))
      .drawBehind { drawRect(color) },
  )
}
