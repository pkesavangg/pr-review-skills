package com.greatergoods.meapp.features.common

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * A sample screen that demonstrates dynamic theme switching and usage of theme tokens.
 *
 * @param isDarkTheme Whether the dark theme is enabled.
 * @param onToggleTheme Callback to toggle the theme.
 * @param modifier Modifier for styling.
 */
@Composable
fun SampleThemeScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primary)
            .padding(MeAppTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isDarkTheme) "Dark Theme" else "Light Theme",
            style = MeAppTheme.typography.heading1,
            color = MeAppTheme.colorScheme.primaryAction
        )
        Button(
            onClick = onToggleTheme,
            colors = ButtonDefaults.buttonColors(containerColor = MeAppTheme.colorScheme.secondaryAction),
            modifier = Modifier.padding(top = MeAppTheme.spacing.md)
        ) {
            Text(
                text = "Toggle Theme",
                style = MeAppTheme.typography.subHeading1,
                color = MeAppTheme.colorScheme.inverse
            )
        }
    }
}

/**
 * Preview of [SampleThemeScreen] in light mode.
 */
@Preview(name = "SampleThemeScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewSampleThemeScreenLight() {
    var isDarkTheme by remember { mutableStateOf(false) }
    MeAppTheme(darkTheme = isDarkTheme) {
        SampleThemeScreen(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { isDarkTheme = !isDarkTheme }
        )
    }
}

/**
 * Preview of [SampleThemeScreen] in dark mode.
 */
@Preview(name = "SampleThemeScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSampleThemeScreenDark() {
    var isDarkTheme by remember { mutableStateOf(true) }
    MeAppTheme(darkTheme = isDarkTheme) {
        SampleThemeScreen(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { isDarkTheme = !isDarkTheme }
        )
    }
} 