package com.dmdbrands.gurus.weight.features.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.loading.string.LoadingString
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.delay

/**
 * Main splash/loading screen with logo and animated "loading..." indicator.
 */
@Composable
fun LoadingScreen() {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MeTheme.colorScheme.primaryAction),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = AppIcons.Default.Banner),
        contentDescription = LoadingString.LOADING,
        colorFilter = ColorFilter.tint(MeTheme.colorScheme.inverseAction),
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.x3l))
      LoadingTextWithDots()
    }

    // Footer
    Box(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(bottom = MeTheme.spacing.md),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = LoadingString.VERSION,
        color = MeTheme.colorScheme.inverseAction,
        style = MeTheme.typography.subHeading2,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/**
 * Composable for animated "loading" text with animated dots as subscript.
 */
@Composable
fun LoadingTextWithDots(
  baseText: String = LoadingString.LOADING,
  dotCount: Int = 3,
  modifier: Modifier = Modifier,
  textColor: Color = MeTheme.colorScheme.inverseAction,
  dotColor: Color = textColor
) {
  Row(
    verticalAlignment = Alignment.Bottom,
    modifier = modifier,
  ) {
    Text(
      text = baseText,
      style = MeTheme.typography.subHeading1,
      color = textColor,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Box(
      modifier =
        Modifier
          .align(Alignment.Bottom),
    ) {
      AnimatedTextDots(dotCount = dotCount, dotColor = dotColor)
    }
  }
}

/**
 * Animated text dots that bounce vertically.
 */
@Composable
private fun AnimatedTextDots(dotCount: Int, dotColor: Color) {
  val animatables = List(dotCount) { remember { Animatable(0f) } }
  val travelDistance = with(LocalDensity.current) { 4.dp.toPx() }

  animatables.forEachIndexed { index, animatable ->
    LaunchedEffect(Unit) {
      delay(index * 120L)
      animatable.animateTo(
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation =
              keyframes {
                durationMillis = 1000
                0.0f at 0
                1.0f at 250 using FastOutSlowInEasing
                0.0f at 500
                0.0f at 1000
              },
            repeatMode = RepeatMode.Restart,
          ),
      )
    }
  }

  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    animatables.forEach { anim ->
      AnimatedDot(yOffset = -anim.value * travelDistance, dotColor)
    }
  }
}

/**
 * Single animated text-based dot.
 */
@Composable
private fun AnimatedDot(yOffset: Float, dotColor: Color) {
  Text(
    text = ".",
    style = MeTheme.typography.subHeading1,
    color = dotColor,
    fontSize = 18.sp,
    modifier =
      Modifier.graphicsLayer {
        translationY = yOffset
      },
  )
}

@PreviewTheme
@Composable
fun LoadingScreenPreviewLight() {
  MeAppTheme {
    LoadingScreen()
  }
}
