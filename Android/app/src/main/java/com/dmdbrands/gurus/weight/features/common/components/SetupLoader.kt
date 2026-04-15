package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
 * Timing contract for [SetupLoader]. Exposed so non-UI callers (ViewModels,
 * pairing managers) can coordinate navigation with the loader's animations
 * without hardcoding magic numbers.
 */
object SetupLoaderTimings {
  /** Duration of the crossfade on the middle dot between Loading and Success/Failed. */
  const val CROSSFADE_MS: Int = 400

  /** Visibility buffer added on top of the crossfade so the final frame is perceived. */
  const val SUCCESS_BUFFER_MS: Long = 400L

  /**
   * Delay callers should wait after emitting [ConnectionState.Success] before
   * navigating away. Covers [CROSSFADE_MS] plus [SUCCESS_BUFFER_MS] for slower
   * devices so the user reliably perceives the checkmark.
   */
  const val SUCCESS_DISPLAY_MS: Long = CROSSFADE_MS + SUCCESS_BUFFER_MS
}

/** Number of dots rendered by [SetupLoader]. */
private const val DOT_COUNT = 5

/** The only dot whose content swaps between Loading and Success/Failed. */
private const val MIDDLE_DOT_INDEX = DOT_COUNT / 2

/**
 * A coarser projection of [ConnectionState] used to drive the visual state of
 * [SetupLoader]. Collapses the [ConnectionState.Failed] sealed hierarchy into a
 * single `Failed` bucket so transitions between error variants (e.g. `Error` ↔
 * `ErrorWithMessage`) do not trigger a redundant crossfade.
 */
private enum class LoaderDisplay { Loading, Success, Failed }

private fun ConnectionState.toLoaderDisplay(): LoaderDisplay = when (this) {
  ConnectionState.Loading -> LoaderDisplay.Loading
  ConnectionState.Success -> LoaderDisplay.Success
  is ConnectionState.Failed -> LoaderDisplay.Failed
}

@Composable
private fun LoaderDisplay.dotColor(): Color = when (this) {
  LoaderDisplay.Loading -> colorScheme.iconPrimary
  LoaderDisplay.Success -> colorScheme.success
  LoaderDisplay.Failed -> colorScheme.danger
}

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
  val display = connectionState.toLoaderDisplay()

  // Animate the outer dots' color over the same window as the middle-dot
  // crossfade so the whole loader transitions as a single visual gesture
  // rather than the middle dot fading while the ring snaps.
  val outerDotColor by animateColorAsState(
    targetValue = display.dotColor(),
    animationSpec = tween(SetupLoaderTimings.CROSSFADE_MS),
    label = "SetupLoaderOuterDotColor",
  )

  // Custom animation state for uniform timing
  var animationProgress by remember { mutableStateOf(0f) }

  LaunchedEffect(display) {
    if (display == LoaderDisplay.Loading) {
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
    repeat(DOT_COUNT) { index ->
      if (index == MIDDLE_DOT_INDEX) {
        MiddleDot(display = display, animationProgress = animationProgress)
      } else {
        AnimatedDot(
          color = outerDotColor,
          shouldAnimate = display == LoaderDisplay.Loading,
          dotIndex = index,
          animationProgress = animationProgress,
        )
      }
    }
  }
}

/**
 * The center dot of the SetupLoader. This is the only dot whose content changes
 * between Loading and Success/Failed, so it is wrapped in an [AnimatedContent]
 * crossfade to avoid a visual snap.
 *
 * Keyed on [LoaderDisplay] (not the full [ConnectionState]) so transitions
 * between [ConnectionState.Failed] subtypes do not trigger a redundant crossfade.
 * The dot color is derived from the `state` parameter inside the lambda so the
 * outgoing composition renders in its own color rather than inheriting the new
 * target's color from the outer scope.
 */
@Composable
private fun MiddleDot(
  display: LoaderDisplay,
  animationProgress: Float,
) {
  AnimatedContent(
    targetState = display,
    transitionSpec = {
      fadeIn(animationSpec = tween(SetupLoaderTimings.CROSSFADE_MS)) togetherWith
        fadeOut(animationSpec = tween(SetupLoaderTimings.CROSSFADE_MS))
    },
    label = "SetupLoaderMiddleDotTransition",
  ) { state ->
    val color = state.dotColor()
    when (state) {
      LoaderDisplay.Success -> AppIcon(
        id = AppIcons.Selection.CircleSelected,
        contentDescription = SetupLoaderStrings.SuccessIconDescription,
        tintColor = color,
      )
      LoaderDisplay.Failed -> AppIcon(
        id = AppIcons.Selection.CircleClosed,
        contentDescription = SetupLoaderStrings.ErrorIconDescription,
        tintColor = color,
      )
      LoaderDisplay.Loading -> AnimatedDot(
        color = color,
        shouldAnimate = true,
        dotIndex = MIDDLE_DOT_INDEX,
        animationProgress = animationProgress,
      )
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
