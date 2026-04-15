package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

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

  // Custom animation state for uniform timing
  var animationProgress by remember { mutableStateOf(0f) }

  LaunchedEffect(connectionState) {
    if (connectionState == ConnectionState.Loading) {
      while (true) {
        animationProgress = (animationProgress + 0.06f) % 5f
        delay(20) // 20ms per frame = 50fps, 1.67 seconds total cycle
      }
    }
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    repeat(5) { index ->
      AnimatedContent(
        targetState = connectionState,
        transitionSpec = {
          fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) togetherWith
            fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
        },
        label = "DotTransition$index",
      ) { state ->
        if ((state == ConnectionState.Success || state is ConnectionState.Failed) && index == 2) {
          // Show icon for middle dot on success/error
          AppIcon(
            id = if (state == ConnectionState.Success) {
              AppIcons.Selection.CircleSelected
            } else {
              AppIcons.Selection.CircleClosed
            },
            contentDescription = if (state == ConnectionState.Success) {
              SetupLoaderStrings.SuccessIconDescription
            } else {
              SetupLoaderStrings.ErrorIconDescription
            },
            tintColor = dotColor,
          )
        } else {
          // Show animated dot with custom uniform timing
          AnimatedDot(
            color = dotColor,
            shouldAnimate = state == ConnectionState.Loading,
            dotIndex = index,
            animationProgress = animationProgress,
          )
        }
      }
    }
  }
}

/**
 * Individual animated dot component with smooth 5-state animation pattern.
 * Each state represents a different arrangement of dot sizes:
 * State 1: big, bigger, biggest, bigger, big
 * State 2: big, big, bigger, biggest, bigger
 * State 3: bigger, big, big, bigger, biggest
 * State 4: biggest, bigger, big, big, bigger
 * State 5: bigger, biggest, bigger, big, big
 *
 * @param color The color of the dot
 * @param shouldAnimate Whether the dot should animate
 * @param dotIndex The index of the dot (0-4)
 * @param animationProgress The shared animation progress (0f to 5f)
 */
@Composable
private fun AnimatedDot(
  color: Color,
  shouldAnimate: Boolean,
  dotIndex: Int,
  animationProgress: Float
) {
  val scale = if (shouldAnimate) {
    // Use custom animation for perfectly uniform timing
    val stateProgress = animationProgress % 5f

    // Calculate exact state boundaries
    val currentState = when {
      stateProgress < 1f -> 0
      stateProgress < 2f -> 1
      stateProgress < 3f -> 2
      stateProgress < 4f -> 3
      else -> 4
    }

    val nextState = (currentState + 1) % 5

    // Calculate interpolation within the current state
    val interpolationProgress = when {
      stateProgress < 1f -> stateProgress
      stateProgress < 2f -> stateProgress - 1f
      stateProgress < 3f -> stateProgress - 2f
      stateProgress < 4f -> stateProgress - 3f
      else -> stateProgress - 4f
    }

    // Define the exact pattern for each state
    val statePatterns = arrayOf(
      // State 0: big, bigger, biggest, bigger, big
      floatArrayOf(1.0f, 1.5f, 2.0f, 1.5f, 1.0f),
      // State 1: big, big, bigger, biggest, bigger
      floatArrayOf(1.0f, 1.0f, 1.5f, 2.0f, 1.5f),
      // State 2: bigger, big, big, bigger, biggest
      floatArrayOf(1.5f, 1.0f, 1.0f, 1.5f, 2.0f),
      // State 3: biggest, bigger, big, big, bigger
      floatArrayOf(2.0f, 1.5f, 1.0f, 1.0f, 1.5f),
      // State 4: bigger, biggest, bigger, big, big
      floatArrayOf(1.5f, 2.0f, 1.5f, 1.0f, 1.0f),
    )

    val currentScale = statePatterns[currentState][dotIndex]
    val nextScale = statePatterns[nextState][dotIndex]

    // Linear interpolation for uniform speed
    currentScale + (nextScale - currentScale) * interpolationProgress
  } else {
    // When not animating, show the middle-focused pattern
    1.0f + (0.2f * 1.5f)
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
      modifier = Modifier
        .fillMaxWidth()
        .padding(spacing.xl),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      SetupLoader(connectionState = ConnectionState.Success)
    }
  }
}
