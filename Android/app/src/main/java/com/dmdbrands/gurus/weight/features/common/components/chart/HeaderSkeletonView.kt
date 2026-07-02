package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.power.powerSaveAwareInfiniteFloat
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Skeleton overlay for the chart header area — matches ChartHeader's 3-row layout.
 * "week average" text shows through (Row 1 is transparent in the skeleton).
 * Rows 2 + 3 are shimmer placeholders matching the real value + range text sizes.
 *
 * Same opacity pulse animation as [GraphSkeletonView].
 */
@Composable
fun HeaderSkeletonView(
  modifier: Modifier = Modifier,
) {
  // Rests at a mid-shimmer value (static dim placeholder) under Power Saving Mode (MOB-226).
  val animPhase = powerSaveAwareInfiniteFloat(
    label = "headerSkeletonAlpha",
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = androidx.compose.animation.core.EaseInOut),
      repeatMode = RepeatMode.Reverse,
    ),
    restingValue = 0.5f,
  )
  val skeletonAlpha = 0.2f + animPhase * 0.2f
  val skeletonColor = MeTheme.colorScheme.textSubheading

  Column(
    modifier = modifier.padding(
      horizontal = MeTheme.spacing.sm,
      vertical = MeTheme.spacing.x3s,
    ),
    horizontalAlignment = Alignment.Start,
  ) {
    // Row 1: transparent — "week average" text shows through from real header
    Spacer(modifier = Modifier.height(18.dp))

    // Row 2: value placeholder (matches heading2 text height)
    Box(
      modifier = Modifier
        .width(120.dp)
        .height(28.dp)
        .clip(RoundedCornerShape(4.dp))
        .drawBehind { drawRect(skeletonColor.copy(alpha = skeletonAlpha)) },
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Row 3: range placeholder (matches subHeading2 text height)
    Box(
      modifier = Modifier
        .width(140.dp)
        .height(14.dp)
        .clip(RoundedCornerShape(4.dp))
        .drawBehind { drawRect(skeletonColor.copy(alpha = skeletonAlpha)) },
    )
  }
}
