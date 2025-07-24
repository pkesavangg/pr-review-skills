package com.greatergoods.meapp.features.common.components

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
import com.greatergoods.meapp.features.ScaleSetup.modal.ConnectionState
import com.greatergoods.meapp.features.common.components.strings.SetupLoaderStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

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

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(spacing.md),
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
        // Show animated dot
        AnimatedDot(
          color = dotColor,
          shouldAnimate = connectionState == ConnectionState.Loading,
          animationDelay = index * 150, // 150ms delay per dot
        )
      }
    }
  }
}

/**
 * Individual animated dot component.
 *
 * @param color The color of the dot
 * @param shouldAnimate Whether the dot should animate
 * @param animationDelay The delay before animation starts (in milliseconds)
 */
@Composable
private fun AnimatedDot(
  color: Color,
  shouldAnimate: Boolean,
  animationDelay: Int
) {
  val infiniteTransition = rememberInfiniteTransition(label = "DotAnimation")

  val scale by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 1.2f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = 600,
        delayMillis = animationDelay,
      ),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "DotScale",
  )

  Box(
    modifier = Modifier
      .size(10.dp)
      .scale(if (shouldAnimate) scale else 1.0f)
      .clip(CircleShape)
      .background(color),
  )
}

@PreviewTheme
@Composable
private fun PreviewSetupLoaderLoading() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      SetupLoader(connectionState = ConnectionState.Loading)
    }
  }
}

@PreviewTheme
@Composable
private fun PreviewSetupLoaderSuccess() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      SetupLoader(connectionState = ConnectionState.Success)
    }
  }
}

@PreviewTheme
@Composable
private fun PreviewSetupLoaderError() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      SetupLoader(connectionState = ConnectionState.Failed.Error)
    }
  }
}
