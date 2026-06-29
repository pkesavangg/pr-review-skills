package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.power.powerSaveAwareInfiniteFloat
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun PulsingDotLoader(
    modifier: Modifier = Modifier,
    color: Color = MeTheme.colorScheme.meAppPrimary,
    minRadius: Float = 6f,
    maxRadius: Float = 12f,
    durationMillis: Int = 700,
) {
    // Drops to a solid dot at maxRadius under Power Saving Mode instead of pulsing (MOB-226).
    val radius = powerSaveAwareInfiniteFloat(
        initialValue = minRadius,
        targetValue = maxRadius,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        restingValue = maxRadius,
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
