package com.greatergoods.meapp.features.sample

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays the Device Settings screen.
 */
@Composable
fun DeviceSettingsScreen() {
    Text("Device Settings Screen")
}

@PreviewTheme
@Composable
private fun PreviewDeviceSettingsScreen() {
    MeAppTheme {
        DeviceSettingsScreen()
    }
}
