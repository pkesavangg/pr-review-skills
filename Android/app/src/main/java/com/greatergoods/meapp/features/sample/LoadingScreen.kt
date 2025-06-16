package com.greatergoods.meapp.features.sample

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.R
import com.greatergoods.meapp.features.common.components.MEImage
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.delay
import android.content.res.Configuration

/**
 * Splash/loading screen matching the provided design.
 */
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primaryAction),
    ) {
        // Centered content (logo + loading)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MEImage(
                lightMode = R.drawable.ic_logo_light,
                darkMode = R.drawable.ic_logo_dark,
                contentDescription = "Loading",
            )
            Spacer(modifier = Modifier.height(32.dp))
            LoadingDotsText()
        }
        // Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "me.health by greater goods",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "version 1.0.0",
                color = MeAppTheme.colorScheme.inverse,
                style = MeAppTheme.typography.subHeading2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Composable for the 'loading . . .' text with colored dots.
 */
@Composable
private fun LoadingDotsText(
    baseText: String = "Loading",
    dotCount: Int = 3,
    delayBetweenDots: Int = 150,
    modifier: Modifier = Modifier
) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            Text(text = baseText, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(4.dp))
            AnimatedDots(dotCount = dotCount, delayBetweenDots = delayBetweenDots)
        }
    }

    @Composable
    fun AnimatedDots(dotCount: Int, delayBetweenDots: Int) {
        Row {
            for (i in 0 until dotCount) {
                AnimatedDot(index = i, delayBetweenDots = delayBetweenDots)
            }
        }
    }

    @Composable
    fun AnimatedDot(index: Int, delayBetweenDots: Int) {
        val transition = rememberInfiniteTransition(label = "dotTransition")
        val offsetY by transition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    delayMillis = index * delayBetweenDots,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "offsetY"
        )

        Text(
            text = ".",
            fontSize = 20.sp,
            modifier = Modifier
                .offset(y = offsetY.dp)
                .padding(horizontal = 1.dp)
        )
    }


@PreviewTheme
@Composable
fun LoadingScreenPreviewLight() {
    MeAppTheme {
        LoadingScreen()
    }
}


