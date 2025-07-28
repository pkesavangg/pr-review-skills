package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.common.components.strings.SetupLoaderStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlin.math.abs
import kotlin.math.max

/**
 * SetupLoader component that displays animated dots during loading
 * and shows appropriate icons for success/error states.
 *
 * @param connectionState The current connection state
 * @param modifier The modifier to be applied to the component
 */
@Composable
fun SetupLoader(
  connectionState: ConnectionState,
  modifier: Modifier = Modifier
) {
  val dotColor = when (connectionState) {
    ConnectionState.Loading -> colorScheme.iconPrimary
    ConnectionState.Success -> colorScheme.success
    else -> colorScheme.danger
  }

  // Single animation value that drives the entire wave
  val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
  val animationProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2000),
      repeatMode = RepeatMode.Restart,
    ),
    label = "WaveProgress",
  )

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(30.dp),
  ) {
    repeat(5) { index ->
      if ((connectionState == ConnectionState.Success || connectionState is ConnectionState.Failed) && index == 2) {
        // Show icon for middle dot on success/error
        AppIcon(
          id = if (connectionState == ConnectionState.Success) {
            AppIcons.Selection.CircleSelected
          } else {
            AppIcons.Selection.CircleClosed
          },
          contentDescription = if (connectionState == ConnectionState.Success) {
            SetupLoaderStrings.SuccessIconDescription
          } else {
            SetupLoaderStrings.ErrorIconDescription
          },
          tintColor = dotColor,
        )
      } else {
        // Show animated dot with uniform speed smooth transitions
        AnimatedDot(
          color = dotColor,
          shouldAnimate = connectionState == ConnectionState.Loading,
          dotIndex = index,
          animationProgress = animationProgress,
        )
      }
    }
  }
}

/**
 * Individual animated dot component with uniform speed smooth transitions.
 *
 * @param color The color of the dot
 * @param shouldAnimate Whether the dot should animate
 * @param dotIndex The index of the dot (0-4) for wave pattern calculation
 * @param animationProgress The shared animation progress (0f to 1f)
 */
@Composable
private fun AnimatedDot(
  color: Color,
  shouldAnimate: Boolean,
  dotIndex: Int,
  animationProgress: Float
) {
  // Create a uniform speed wave with seamless transitions
  // Use a continuous wave that flows smoothly without sudden jumps
  val waveCenter = if (shouldAnimate) {
    // Create a smooth, continuous wave that flows at uniform speed
    val progress = animationProgress * 4f
    // Use modulo for seamless wrapping without sudden jumps
    progress % 4f
  } else {
    2f // Default to middle dot being largest when not animating
  }

  // Calculate distance with smooth wrapping for seamless transitions
  val distance = if (shouldAnimate) {
    val directDistance = abs(dotIndex - waveCenter)
    // Handle wrapping for seamless transitions
    val wrappedDistance = abs(dotIndex - waveCenter + 4f)
    val wrappedDistance2 = abs(dotIndex - waveCenter - 4f)
    minOf(directDistance, wrappedDistance, wrappedDistance2)
  } else {
    abs(dotIndex - 2f) // Distance from middle dot when not animating
  }

  // Create a smooth wave effect where dots closer to the center are larger
  val scale = if (shouldAnimate) {
    // Use a smooth falloff: dots at the center are largest, dots further away are smaller
    val waveIntensity = max(0f, 1f - (distance * 0.6f)) // 0.6f for smooth wave width
    // Map wave intensity to scale: 1.0 (10dp) to 2.5 (25dp)
    1.0f + (waveIntensity * 1.5f)
  } else {
    // When not animating, show the middle-focused pattern
    val staticDistance = abs(dotIndex - 2f)
    val staticIntensity = max(0f, 1f - (staticDistance * 0.6f))
    1.0f + (staticIntensity * 1.5f)
  }

  Box(
    modifier = Modifier
      .size(10.dp)
      .scale(scale)
      .clip(CircleShape)
      .background(color),
  )
}

@PreviewTheme
@Composable
private fun PreviewSetupLoaderLoading() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth().padding(spacing.xl),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      SetupLoader(connectionState = ConnectionState.Loading)
    }
  }
}

// @PreviewTheme
// @Composable
// private fun PreviewSetupLoaderSuccess() {
//   MeAppTheme {
//     Column(
//       modifier = Modifier.fillMaxWidth(),
//       horizontalAlignment = Alignment.CenterHorizontally,
//       verticalArrangement = Arrangement.spacedBy(spacing.xl),
//     ) {
//       SetupLoader(connectionState = ConnectionState.Success)
//     }
//   }
// }

// @PreviewTheme
// @Composable
// private fun PreviewSetupLoaderError() {
//   MeAppTheme {
//     Column(
//       modifier = Modifier.fillMaxWidth(),
//       horizontalAlignment = Alignment.CenterHorizontally,
//       verticalArrangement = Arrangement.spacedBy(spacing.xl),
//     ) {
//       SetupLoader(connectionState = ConnectionState.Failed.Error)
//     }
//   }
// }
