package com.greatergoods.meapp.features.common.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppLinearProgressIndicator
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import kotlinx.coroutines.delay

/**
 * Sample screen demonstrating the expressive AppLinearProgressIndicator with a Start button.
 * When Start is pressed, the progress animates from 0 to 1.
 */
@Composable
fun ExpressiveProgressSampleScreen() {
    var progress by remember { mutableStateOf(0f) }
    var isRunning by remember { mutableStateOf(false) }

    // Animate progress from 0 to 1 when started
    LaunchedEffect(isRunning) {
        if (isRunning) {
            progress = 0f
            while (progress < 1f) {
                delay(20)
                progress += 0.01f
            }
            progress = 1f
            isRunning = false
        }
    }

    MeAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MeAppTheme.spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Expressive Progress Demo", style = MeAppTheme.typography.heading4, color = MeAppTheme.colorScheme.heading)
            Spacer(modifier = Modifier.height(16.dp))
            AppLinearProgressIndicator(
                progress = progress,
                showDot = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { if (!isRunning) isRunning = true }, enabled = !isRunning) {
                Text("Start")
            }
        }
    }
}

@PreviewTheme
@Composable
fun ExpressiveProgressSampleScreenPreview() {
    ExpressiveProgressSampleScreen()
}
