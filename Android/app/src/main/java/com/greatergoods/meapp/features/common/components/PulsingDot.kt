package com.greatergoods.meapp.features.common.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun PulsingDotLoader(
    modifier: Modifier = Modifier,
    color: Color = MeTheme.colorScheme.meAppPrimary,
    minRadius: Float = 6f,
    maxRadius: Float = 12f,
    durationMillis: Int = 700,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing-dot")
    val radius by infiniteTransition.animateFloat(
        initialValue = minRadius,
        targetValue = maxRadius,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "radius",
    )

    Canvas(modifier = modifier.size((maxRadius * 2).dp)) {
        drawCircle(
            color = color,
            radius = radius,
        )
    }
}

@PreviewTheme
@Composable
fun PulsingDotLoaderPreview() {
    MeAppTheme {
        AppScaffold("") {
            PulsingDotLoader()
        }
    }
}
