package com.greatergoods.meapp.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
private fun LoadingDotsText() {
    val dotColor = MeAppTheme.colorScheme.brand
    var dotCount by remember { mutableStateOf(1) }

    // Animate the dot count
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = if (dotCount == 3) 1 else dotCount + 1
        }
    }

    Text(
        buildAnnotatedString {
            append("loading ")
            repeat(dotCount) { i ->
                withStyle(
                    SpanStyle(
                        color = if (i == 2) dotColor else MeAppTheme.colorScheme.inverse
                    )
                ) { append(".") }
            }
        },
        fontSize = 16.sp,
        color = MeAppTheme.colorScheme.inverse,
        style = MeAppTheme.typography.subHeading1,
        fontWeight = FontWeight.Normal,
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun LoadingScreenPreviewLight() {
    MeAppTheme {
        LoadingScreen()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun LoadingScreenPreviewDark() {
    MeAppTheme {
        LoadingScreen()
    }
}
