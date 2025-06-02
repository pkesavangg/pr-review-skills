package com.greatergoods.meapp.features.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
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
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme
import android.content.res.Configuration

/**
 * Dialog for selecting the theme mode.
 *
 * @param selectedMode The currently selected theme mode.
 * @param onModeSelected Callback to update the theme mode.
 * @param onDismiss Callback to dismiss the dialog.
 */
@Composable
fun SampleThemeDialog(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var localSelectedMode by remember { mutableStateOf(selectedMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme Mode") },
        text = {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
                if (localSelectedMode == ThemeMode.UNRECOGNIZED) {
                    Text(
                        "Unknown theme mode selected! Please choose a valid option.",
                        color = MeAppTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                ThemeMode.entries.filter { it != ThemeMode.UNRECOGNIZED }.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = localSelectedMode == mode,
                            onClick = { localSelectedMode = mode },
                        )
                        Text(text = mode.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onModeSelected(localSelectedMode)
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@PreviewTheme
@Composable
private fun PreviewSampleThemeDialog() {
    MeAppTheme {
        SampleThemeDialog(selectedMode = ThemeMode.LIGHT, onModeSelected = {}, onDismiss = {})
    }
}
