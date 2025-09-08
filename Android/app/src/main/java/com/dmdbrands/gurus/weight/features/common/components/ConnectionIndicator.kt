package com.dmdbrands.gurus.weight.features.common.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Represents the connection state for the connection indicator.
 */
enum class ConnectionIndicatorState {
  /** Connecting state with pulse animation */
  Connecting,

  /** Failed state with error styling */
  Failed
}

/**
 * ConnectionIndicator component that displays connection status with pulse animation.
 *
 * @param indicatorIcon The icon resource to display in the center
 * @param connectionState The current connection state
 * @param modifier The modifier to be applied to the component
 */
@Composable
fun ConnectionIndicator(
  @DrawableRes indicatorIcon: Int,
  showIndicatorAlone: Boolean = false,
  connectionState: ConnectionIndicatorState,
  modifier: Modifier = Modifier
) {
  val mainCircleColor = when (connectionState) {
    ConnectionIndicatorState.Connecting -> colorScheme.iconPrimary
    ConnectionIndicatorState.Failed -> colorScheme.danger
  }

  val pulsingCircleColor = when (connectionState) {
    ConnectionIndicatorState.Connecting -> colorScheme.iconPrimary.copy(alpha = 0.3f)
    ConnectionIndicatorState.Failed -> colorScheme.danger.copy(alpha = 0.3f)
  }

  val contentDescription = when (connectionState) {
    ConnectionIndicatorState.Connecting -> "Connection in progress"
    ConnectionIndicatorState.Failed -> "Connection Failed"
  }

  Box(
    modifier = modifier.size(if (showIndicatorAlone) 90.dp else 170.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Large pulsing circle (only when connecting, behind everything)
    if (!showIndicatorAlone) {
      PulsingCircle(
        color = pulsingCircleColor,
        shouldAnimate = true,
      )
    }
    // Main solid circle
    Box(
      modifier = Modifier
        .size(90.dp)
        .clip(CircleShape)
        .background(mainCircleColor),
    )

    // White icon on top
    Image(
      painter = painterResource(id = indicatorIcon),
      contentDescription = contentDescription,
      modifier = Modifier.size(60.dp),
    )
  }
}

/**
 * Pulsing circle background component for connection indicator.
 * Creates a light colored background that pulses during connection.
 *
 * @param color The color of the circle (should already include desired alpha)
 * @param shouldAnimate Whether the circle should animate with pulse effect
 */
@Composable
private fun PulsingCircle(
  color: Color,
  shouldAnimate: Boolean
) {
  val infiniteTransition = rememberInfiniteTransition(label = "PulseAnimation")

  val scale by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 1.1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "PulseScale",
  )

  Box(
    modifier = Modifier
      .size(170.dp)
      .scale(if (shouldAnimate) scale else 1.0f)
      .clip(CircleShape)
      .background(color)
      .blur(radius = 4.dp),
  )
}

@PreviewTheme
@Composable
private fun PreviewConnectionIndicatorConnecting() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.WifiIndicator,
        connectionState = ConnectionIndicatorState.Connecting,
        showIndicatorAlone = true,
      )
      // WiFi connecting
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.WifiIndicator,
        connectionState = ConnectionIndicatorState.Connecting,
      )

      // Bluetooth connecting
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.BluetoothIndicator,
        connectionState = ConnectionIndicatorState.Connecting,
      )
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.ErrorIndicator,
        connectionState = ConnectionIndicatorState.Connecting,
      )

      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.BrandLogo,
        connectionState = ConnectionIndicatorState.Connecting,
      )
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.WgLogo,
        connectionState = ConnectionIndicatorState.Connecting,
      )
    }
  }
}

@PreviewTheme
@Composable
private fun PreviewConnectionIndicatorFailed() {
  MeAppTheme {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
      // WiFi failed
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.WifiIndicator,
        connectionState = ConnectionIndicatorState.Failed,
      )

      // Bluetooth failed
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.BluetoothIndicator,
        connectionState = ConnectionIndicatorState.Failed,
      )

      // Exclamation (general error)
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.ErrorIndicator,
        connectionState = ConnectionIndicatorState.Failed,
      )

      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.BrandLogo,
        connectionState = ConnectionIndicatorState.Failed,
      )
      ConnectionIndicator(
        indicatorIcon = AppIcons.Default.WgLogo,
        connectionState = ConnectionIndicatorState.Failed,
      )
    }
  }
}
