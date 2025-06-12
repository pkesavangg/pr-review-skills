package com.greatergoods.meapp.features.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import com.greatergoods.meapp.theme.MeAppTheme.animation
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme

/**
 * A reusable, theme-aware linear progress indicator using Material3 Expressive API.
 *
 * - Uses the new expressive LinearProgressIndicator (Material3 1.3.2+).
 * - Supports animated progress updates (0.0 to 1.0).
 * - Uses theme tokens for default colors, but allows color overrides.
 * - Optionally displays a trailing dot at the end of the progress bar using drawStopIndicator.
 * - Can be used inline in lists, cards, or sections.
 *
 * @param progress The current progress, from 0.0 (empty) to 1.0 (full).
 * @param modifier Modifier for styling and layout.
 * @param showDot If true, shows a trailing dot at the end of the progress bar.
 * @param progressColor Optional override for the progress (filled) color.
 * @param trackColor Optional override for the track (background) color.
 * @param height The height of the progress bar.
 * @param strokeCap The stroke cap for the progress bar (default: Round).
 * @param gapSize The gap size for the expressive progress bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    progressColor: Color? = null,
    trackColor: Color? = null,
    height: Dp = 8.dp,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    gapSize: Dp = ProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = animation.mediumDuration,
            easing = FastOutSlowInEasing
        ),
        label = "Progress Animation"
    )
    val fillColor = progressColor ?: colorScheme.primaryAction
    val bgColor = trackColor ?: colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.CenterStart
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = fillColor,
            trackColor = bgColor,
            strokeCap = strokeCap,
            gapSize = gapSize,
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            drawStopIndicator = if (showDot) {
                {
                    // Define your dot size
                    val dotRadius = height.toPx() / 2 // Make dot radius half of the bar height

                    // Calculate the x position of the dot.
                    // It should be at the end of the filled progress.
                    // We need to ensure the dot is fully visible within the bounds.
                    val dotX = (size.width * animatedProgress).coerceIn(dotRadius, size.width - dotRadius)

                    // Calculate the y position (center of the bar)
                    val dotY = size.height / 2f

                    // Draw the circle
                    drawCircle(
                        color = fillColor,
                        radius = dotRadius,
                        center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                    )
                }
            } else {
                // If showDot is false, provide an empty lambda to do nothing.
                { }
            }
        )
    }
}

// --- Previews ---

@PreviewTheme
@Composable
fun AppLinearProgressIndicatorPreview_25() {
    MeAppTheme {
        AppLinearProgressIndicator(progress = 0.25f, showDot = true)
    }
}

@PreviewTheme
@Composable
fun AppLinearProgressIndicatorPreview_50() {
    MeAppTheme {
        AppLinearProgressIndicator(progress = 0.5f, showDot = true)
    }
}

@PreviewTheme
@Composable
fun AppLinearProgressIndicatorPreview_75() {
    MeAppTheme {
        AppLinearProgressIndicator(progress = 0.75f, showDot = true)
    }
}

@PreviewTheme
@Composable
fun AppLinearProgressIndicatorPreview_Animated() {
    // Animated preview: progress animates from 0 to 1
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // No-op: Compose Preview does not support real-time animation, but this is for code reference
    }
    MeAppTheme {
        AppLinearProgressIndicator(progress = 1.0f, showDot = true)
    }
}
