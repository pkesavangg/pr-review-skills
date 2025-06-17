package com.greatergoods.meapp.features.common.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeAppTheme.animation
import com.greatergoods.meapp.theme.MeAppTheme.colorScheme

enum class AppLinearProgressType {
    Primary,
    Success,
}

object AppLinearProgressDefaults {
    @Composable
    fun getProgressColor(type: AppLinearProgressType): Color =
        when (type) {
            AppLinearProgressType.Primary -> colorScheme.primaryAction
            AppLinearProgressType.Success -> colorScheme.success
        }

    @Composable
    fun getTrackColor(type: AppLinearProgressType): Color =
        when (type) {
            AppLinearProgressType.Primary -> colorScheme.utility
            AppLinearProgressType.Success -> colorScheme.utility
        }
}

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
 * @param height The height of the progress bar.
 * @param type The type of progress bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    type: AppLinearProgressType = AppLinearProgressType.Primary,
    height: Dp = 12.dp,
) {
    val progressColor: Color = AppLinearProgressDefaults.getProgressColor(type)
    val trackColor: Color = AppLinearProgressDefaults.getTrackColor(type)
    val strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap
    val gapSize: Dp = ProgressIndicatorDefaults.LinearIndicatorTrackGapSize

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec =
            tween(
                durationMillis = animation.mediumDuration,
                easing = FastOutSlowInEasing,
            ),
        label = "Progress Animation",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = progressColor,
            trackColor = trackColor,
            strokeCap = strokeCap,
            gapSize = gapSize,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height),
        )
    }
}

@PreviewTheme
@Composable
fun AppLinearProgressIndicatorPreview() {
    MeAppTheme {
        Column {
            AppLinearProgressIndicator(progress = 0.25f)
            AppLinearProgressIndicator(progress = 0.5f)
            AppLinearProgressIndicator(progress = 0.75f)
            AppLinearProgressIndicator(progress = 1.0f)
        }
    }
}
