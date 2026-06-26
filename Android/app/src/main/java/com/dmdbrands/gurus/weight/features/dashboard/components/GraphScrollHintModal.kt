package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.core.power.powerSaveAwareInfiniteFloat
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.ModalConfigs
import com.dmdbrands.gurus.weight.features.common.components.ModalDialog
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextTransform
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

private val CardWidth = 312.dp
private val CardCorner = 28.dp
private val GraphWidth = 212.dp
private val GraphHeight = 153.dp

@Composable
fun GraphScrollHintModal(
  onDismiss: () -> Unit,
) {
  ModalDialog(
    onDismiss = onDismiss,
    config = ModalConfigs.Informational,
  ) {
    Card(
      modifier = Modifier.width(CardWidth),
      shape = RoundedCornerShape(CardCorner),
      colors = CardDefaults.cardColors(
        containerColor = MeTheme.colorScheme.primaryBackground,
      ),
    ) {
      Column(
        modifier = Modifier.padding(MeTheme.spacing.md),
      ) {
        Row(modifier = Modifier.fillMaxWidth()) {
          Spacer(modifier = Modifier.weight(1f))
          AppIcon(
            id = AppIcons.Default.closeFilled,
            modifier = Modifier.size(24.dp),
            contentDescription = AppPopupStrings.GraphScrollHint.CloseContentDescription,
            type = AppIconType.Primary,
            tintColor = Color.Unspecified,
            onClick = onDismiss,
          )
        }
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AnimatedHintGraph(
            modifier = Modifier
              .width(GraphWidth)
              .height(GraphHeight),
          )
          Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
          Text(
            text = AppPopupStrings.GraphScrollHint.Header,
            style = MeTheme.typography.heading4,
            color = MeTheme.colorScheme.textHeading,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
          Text(
            text = AppPopupStrings.GraphScrollHint.Message,
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textBody,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
          AppButton(
            label = AppPopupStrings.GraphScrollHint.ConfirmButton,
            type = ButtonType.PrimaryFilled,
            size = ButtonSize.Large,
            textTransform = TextTransform.UPPERCASE,
            onClick = onDismiss,
          )
        }
      }
    }
  }
}

// Normalized 0..1 heights for the demo dataset, mirroring the iOS counterpart.
// Time flows left → right (oldest → newest); values trend downward — the typical
// weight-loss arc users see in the real graph. Combined with the swipe motion
// this produces: swipe right (older data into view) → line elevates upward,
// swipe left (return to latest) → line descends.
//
// Kept short so each swipe pans the chart by a small, hand-paced amount rather
// than racing across the whole dataset.
private val DemoPoints = listOf(
  0.78f, 0.62f, 0.70f, 0.55f, 0.66f, 0.48f,
  0.40f, 0.46f, 0.32f, 0.40f, 0.28f, 0.18f,
)

// Number of points visible inside the viewport at any time.
private const val VisiblePoints = 6

/** Renders the small chart with a finger glyph that pans the data left/right on a loop. */
@Composable
private fun AnimatedHintGraph(modifier: Modifier = Modifier) {
  // Under Power Saving Mode the hint holds a static frame (chart at rest, hand visible at the
  // start position) instead of looping the pan/hand animation (MOB-226).
  val totalSteps = (DemoPoints.size - VisiblePoints).toFloat()
  val maxOffset = totalSteps.coerceAtLeast(1f)

  // Total loop = 8 600 ms. The motion is split into discrete "swipes" so the
  // hand grabs, drags, then lifts/resets between each swipe — matching the
  // iOS 3-history-swipe + 2-return-swipe pattern.
  //
  // Pan and hand share the exact same drag windows and easing so the chart
  // moves only while the hand is actively dragging, then both come to rest
  // together. Between swipes the pan holds steady while the hand lifts
  // (alpha dip) and snaps back to its start position for the next drag.
  val third = maxOffset / 3f
  val twoThirds = maxOffset * 2f / 3f
  val half = maxOffset / 2f

  val panProgress = powerSaveAwareInfiniteFloat(
    initialValue = 0f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 8_600
        // Pan-back: 3 ratcheted glides (each 700 ms, exactly matching the hand drags)
        0f at 0
        0f at 250
        third at 950 using FastOutSlowInEasing
        third at 1_500
        twoThirds at 2_200 using FastOutSlowInEasing
        twoThirds at 2_750
        maxOffset at 3_450 using FastOutSlowInEasing
        maxOffset at 5_300
        // Return: 2 glides
        half at 6_000 using FastOutSlowInEasing
        half at 6_550
        0f at 7_250 using FastOutSlowInEasing
        0f at 8_600
      },
      repeatMode = RepeatMode.Restart,
    ),
    restingValue = 0f,
    label = "pan-progress",
  )

  // Hand position: 0 = left side, 1 = right side. During pan-back it drags
  // left → right; during return it drags right → left. Between swipes it
  // snaps back to the start side while the alpha is dipped so the snap
  // doesn't read as a teleport.
  val handPosition = powerSaveAwareInfiniteFloat(
    initialValue = 0f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 8_600
        // Pan-back drags: left → right (synced with panProgress glides above)
        0f at 0
        0f at 250
        1f at 950 using FastOutSlowInEasing
        1f at 1_150
        0f at 1_400 using FastOutSlowInEasing
        0f at 1_500
        1f at 2_200 using FastOutSlowInEasing
        1f at 2_400
        0f at 2_650 using FastOutSlowInEasing
        0f at 2_750
        1f at 3_450 using FastOutSlowInEasing
        1f at 5_300
        // Return drags: right → left
        0f at 6_000 using FastOutSlowInEasing
        0f at 6_200
        1f at 6_450 using FastOutSlowInEasing
        1f at 6_550
        0f at 7_250 using FastOutSlowInEasing
        0f at 8_600
      },
      repeatMode = RepeatMode.Restart,
    ),
    restingValue = 0f,
    label = "hand-position",
  )

  // Hand alpha fades fully out during the long pause and during the gap
  // between pan-back and return phases. Between drags it dips to 0.25 so the
  // "lift and reset" reads as the same gesture lifting off the screen.
  val handAlpha = powerSaveAwareInfiniteFloat(
    initialValue = 0f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = keyframes {
        durationMillis = 8_600
        0f at 0
        1f at 250
        1f at 1_050
        0.25f at 1_200
        0.25f at 1_400
        1f at 1_550
        1f at 2_300
        0.25f at 2_450
        0.25f at 2_650
        1f at 2_800
        1f at 3_550
        0f at 3_850
        0f at 5_150
        1f at 5_300
        1f at 6_100
        0.25f at 6_250
        0.25f at 6_450
        1f at 6_600
        1f at 7_350
        0f at 7_650
        0f at 8_600
      },
      repeatMode = RepeatMode.Restart,
    ),
    restingValue = 1f,
    label = "hand-alpha",
  )

  val graphBackground = MeTheme.colorScheme.secondaryBackground
  val gridColor = MeTheme.colorScheme.utility
  val lineColor = MeTheme.colorScheme.primaryAction
  val dotColor = MeTheme.colorScheme.primaryAction

  Box(modifier = modifier) {
    Canvas(modifier = Modifier.size(GraphWidth, GraphHeight)) {
    val w = size.width
    val h = size.height
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())

    // Background plate
    drawRoundRect(
      color = graphBackground,
      cornerRadius = cornerRadius,
    )

    // Four horizontal gridlines spanning the full chart width (matches Figma).
    val gridStrokePx = 1.dp.toPx()
    val gridYs = listOf(h * 0.20f, h * 0.45f, h * 0.70f, h * 0.92f)
    gridYs.forEach { y ->
      drawLine(
        color = gridColor.copy(alpha = 0.35f),
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = gridStrokePx,
      )
    }

    // Chart drawing area inset so dots aren't clipped at the edges.
    val padX = 12.dp.toPx()
    val padY = 18.dp.toPx()
    val plotW = w - padX * 2
    val plotH = h - padY * 2

    // The full polyline spans all 18 points; we translate it horizontally so the
    // visible viewport (VisiblePoints wide) slides across the dataset in sync
    // with the hand. At panProgress = 0 the latest points are visible (line shifted
    // fully left); at panProgress = maxOffset the oldest are visible (line at x=0).
    val stepPx = plotW / (VisiblePoints - 1).toFloat()
    val tx = -(maxOffset - panProgress) * stepPx

    // Clip to the rounded chart card so the line doesn't bleed outside the panel.
    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
      val allPositions = ArrayList<Offset>(DemoPoints.size)
      for (i in DemoPoints.indices) {
        val x = padX + i * stepPx + tx
        val y = padY + (1f - DemoPoints[i]) * plotH
        allPositions += Offset(x, y)
      }

      // Catmull-Rom-style smoothing (matches iOS): each segment is a cubic Bézier
      // whose control points sit slightly off the straight line between samples,
      // rounding the corners without turning the chart into a wavy curve.
      val linePath = Path().apply {
        if (allPositions.isNotEmpty()) {
          moveTo(allPositions[0].x, allPositions[0].y)
          val smoothing = 0.35f
          for (i in 1 until allPositions.size) {
            val p0 = allPositions[maxOf(i - 2, 0)]
            val p1 = allPositions[i - 1]
            val p2 = allPositions[i]
            val p3 = allPositions[minOf(i + 1, allPositions.lastIndex)]
            val c1x = p1.x + (p2.x - p0.x) * smoothing / 3f
            val c1y = p1.y + (p2.y - p0.y) * smoothing / 3f
            val c2x = p2.x - (p3.x - p1.x) * smoothing / 3f
            val c2y = p2.y - (p3.y - p1.y) * smoothing / 3f
            cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
          }
        }
      }

      drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
      )

      val dotRadius = 3.dp.toPx()
      val visibleMargin = dotRadius * 2
      allPositions.forEach { p ->
        if (p.x in -visibleMargin..(w + visibleMargin)) {
          drawCircle(color = dotColor, radius = dotRadius, center = p)
        }
      }
    }
    }

    // Hand glyph — ratchets across the chart in short swipes, fading down between
    // drags so each cycle reads as a discrete grab-drag-lift gesture.
    val handSize = 38.dp
    val travelLeftFraction = 0.15f
    val travelRightFraction = 0.70f
    val handX = (GraphWidth - handSize) *
      (travelLeftFraction + (travelRightFraction - travelLeftFraction) * handPosition)
    val handY = GraphHeight * 0.42f
    Image(
      painter = painterResource(id = R.drawable.ic_swipe_hand),
      contentDescription = null,
      modifier = Modifier
        .offset(x = handX, y = handY)
        .size(handSize)
        .alpha(handAlpha),
    )
  }
}

@PreviewTheme
@Composable
private fun GraphScrollHintModalPreview() {
  MeAppTheme {
    GraphScrollHintModal(onDismiss = {})
  }
}
