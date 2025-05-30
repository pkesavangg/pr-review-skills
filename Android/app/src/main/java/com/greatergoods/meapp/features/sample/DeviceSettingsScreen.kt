package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays the Device Settings screen.
 */
@Composable
fun DeviceSettingsScreen() {
    Text("Device Settings Screen")
}

@Preview(name = "DeviceSettingsScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewDeviceSettingsScreenLight() {
    MeAppTheme(darkTheme = false) {
        DeviceSettingsScreen()
    }
}

@Preview(name = "DeviceSettingsScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDeviceSettingsScreenDark() {
    MeAppTheme(darkTheme = true) {
        DeviceSettingsScreen()
    }
} 