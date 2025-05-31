package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import com.greatergoods.meapp.theme.MeAppTheme
import android.content.res.Configuration

/**
 * Displays a sample theme screen with a button to open a dialog for theme mode selection.
 *
 * @param selectedMode The currently selected theme mode.
 * @param onModeSelected Callback to update the theme mode.
 */
@Composable
fun SampleThemeScreen(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primary),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Select Theme Mode", style = MeAppTheme.typography.body1, color = MeAppTheme.colorScheme.body)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showDialog = true }) {
            Text("Choose Theme")
        }
        if (showDialog) {
            SampleThemeDialog(
                selectedMode = selectedMode,
                onModeSelected = {
                    onModeSelected(it)
                    showDialog = false
                },
                onDismiss = { showDialog = false },
            )
        }
    }
}

@Preview(name = "SampleThemeScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewSampleThemeScreenLight() {
    MeAppTheme(darkTheme = false) {
        SampleThemeScreen(selectedMode = ThemeMode.LIGHT, onModeSelected = {})
    }
}

@Preview(name = "SampleThemeScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSampleThemeScreenDark() {
    MeAppTheme(darkTheme = true) {
        SampleThemeScreen(selectedMode = ThemeMode.DARK, onModeSelected = {})
    }
}

