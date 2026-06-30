package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.power.powerSaveAwareInfiniteFloat
import com.dmdbrands.gurus.weight.theme.MeAppTheme

@Composable
fun DashedCircularLoader(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF302A27), // Match your design color
    strokeWidth: Float = 5f,
    dashLength: Float = 18f,
    gapLength: Float = 10f,
    sweepAngle: Float = 300f, // Partial circle
) {
    // Stops rotating and draws a static dashed arc under Power Saving Mode (MOB-226).
    val startAngle = powerSaveAwareInfiniteFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        restingValue = 0f,
        label = "angle",
    )

    Canvas(modifier = modifier.size(45.dp)) {
        size.minDimension
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style =
                Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f),
                ),
        )
    }
}

@PreviewTheme
@Composable
fun DashedCircularLoaderPreview() {
    MeAppTheme {
        DashedCircularLoader()
    }
}
