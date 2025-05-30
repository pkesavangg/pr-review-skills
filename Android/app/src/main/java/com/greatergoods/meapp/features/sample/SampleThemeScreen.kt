package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays a sample theme screen with a toggle for dark/light mode.
 *
 * @param isDark Whether the theme is dark mode.
 * @param onToggleTheme Callback to toggle the theme.
 */
@Composable
fun SampleThemeScreen(isDark: Boolean, onToggleTheme: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isDark) "Dark Theme" else "Light Theme",
            color = if (isDark) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onToggleTheme) {
            Text("Toggle Theme")
        }
    }
}

@Preview(name = "SampleThemeScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewSampleThemeScreenLight() {
    MeAppTheme(darkTheme = false) {
        SampleThemeScreen(isDark = false, onToggleTheme = {})
    }
}

@Preview(name = "SampleThemeScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSampleThemeScreenDark() {
    MeAppTheme(darkTheme = true) {
        SampleThemeScreen(isDark = true, onToggleTheme = {})
    }
}

