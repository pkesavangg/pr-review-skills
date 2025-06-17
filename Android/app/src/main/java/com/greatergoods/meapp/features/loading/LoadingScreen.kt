package com.greatergoods.meapp.features.loading

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.R
import com.greatergoods.meapp.features.common.components.MEImage
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.delay
import com.greatergoods.meapp.features.loading.string.LoadingString
import com.greatergoods.meapp.resources.AppIcons

/**
 * Main splash/loading screen with logo and animated "loading..." indicator.
 */
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primaryAction)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spacer that takes less space to push content slightly above center
            Spacer(modifier = Modifier.weight(0.5f)) // less than half

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MEImage(
                    lightMode = AppIcons.Default.Logo,
                    darkMode = AppIcons.Default.LogoDark,
                    contentDescription = LoadingString.LOADING
                )
                Spacer(modifier = Modifier.height(32.dp))
                LoadingTextWithDots()
            }

            // Spacer that takes more space below
            Spacer(modifier = Modifier.weight(0.6f)) // more than half
        }

        // Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = LoadingString.FOOTER_BRAND,
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading2,
                textAlign = TextAlign.Center
            )
            Text(
                text = LoadingString.FOOTER_VERSION,
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading2,
                textAlign = TextAlign.Center
            )
        }
    }
}


/**
 * Composable for animated "loading" text with animated dots as subscript.
 */
@Composable
private fun LoadingTextWithDots(
    baseText: String = LoadingString.LOADING,
    dotCount: Int = 3,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        Text(
            text = baseText,
            style = MeAppTheme.typography.subHeading1,
            color = MeAppTheme.colorScheme.inverse
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .align(Alignment.Bottom)
        ) {
            AnimatedTextDots(dotCount = dotCount)
        }
    }
}

/**
 * Animated text dots that bounce vertically.
 */
@Composable
private fun AnimatedTextDots(dotCount: Int) {
    val animatables = List(dotCount) { remember { Animatable(0f) } }
    val travelDistance = with(LocalDensity.current) { 4.dp.toPx() }

    animatables.forEachIndexed { index, animatable ->
        LaunchedEffect(Unit) {
            delay(index * 120L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0.0f at 0
                        1.0f at 250 with FastOutSlowInEasing
                        0.0f at 500
                        0.0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        animatables.forEach { anim ->
            AnimatedDot(yOffset = -anim.value * travelDistance)
        }
    }
}

/**
 * Single animated text-based dot.
 */
@Composable
private fun AnimatedDot(yOffset: Float) {
    Text(
        text = ".",
        style = MeAppTheme.typography.subHeading1,
        color = MeAppTheme.colorScheme.wgPrimary,
        fontSize = 20.sp,
        modifier = Modifier.graphicsLayer {
            translationY = yOffset
        }
    )
}

@PreviewTheme
@Composable
fun LoadingScreenPreviewLight() {
    MeAppTheme {
        LoadingScreen()
    }
}
