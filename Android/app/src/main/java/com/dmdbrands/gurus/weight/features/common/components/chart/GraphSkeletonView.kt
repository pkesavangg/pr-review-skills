package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Skeleton loader matching iOS GraphSkeletonView.swift exactly.
 * Grid lines use Compose views (not Canvas) to match iOS Rectangle() rendering.
 */
@Composable
fun GraphSkeletonView(
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "graphSkeletonPulse")
  val animPhase by infiniteTransition.animateFloat(
    label = "graphSkeletonAlpha",
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = androidx.compose.animation.core.EaseInOut),
      repeatMode = RepeatMode.Reverse,
    ),
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
        // Horizontal grid lines — VStack(spacing: 0) with Spacer
        Column(modifier = Modifier.fillMaxSize()) {
          for (i in 0 until yAxisTickCount) {
            if (i > 0) Spacer(modifier = Modifier.weight(1f))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(skeletonColor.copy(alpha = gridLineAlpha)),
            )
          }
        }

        // Vertical grid lines — HStack(spacing: 0) with Spacer
        Row(modifier = Modifier.fillMaxSize()) {
          for (i in 0 until xAxisTickCount) {
            if (i > 0) Spacer(modifier = Modifier.weight(1f))
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(skeletonColor.copy(alpha = gridLineAlpha)),
            )
          }
        }

        // Simulated chart line — .padding(.vertical, 20)
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
                color = skeletonColor.copy(alpha = skeletonAlpha),
                style = Stroke(width = 3.dp.toPx()),
              )
            },
        )
      }

      // Y-axis labels on the right — VStack(spacing: 0), .frame(width: 30)
      Column(
        modifier = Modifier
          .width(30.dp)
          .fillMaxSize(),
      ) {
        for (i in 0 until yAxisTickCount) {
          if (i > 0) Spacer(modifier = Modifier.weight(1f))
          Box(
            modifier = Modifier
              .width(24.dp)
              .height(12.dp)
              .clip(RoundedCornerShape(2.dp))
              .drawBehind { drawRect(skeletonColor.copy(alpha = skeletonAlpha)) },
          )
        }
      }
    }

    // X-axis labels at the bottom — HStack(spacing: 0)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = MeTheme.spacing.xs, end = 38.dp, top = 8.dp),
    ) {
      for (i in 0 until xAxisTickCount) {
        if (i > 0) Spacer(modifier = Modifier.weight(1f))
        Box(
          modifier = Modifier
            .width(24.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .drawBehind { drawRect(skeletonColor.copy(alpha = skeletonAlpha)) },
        )
      }
    }
  }
}
