package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.data.storage.datastore.ThemeMode
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays the Device Overview screen.
 */
@Composable
fun DeviceOverviewScreen() {
    Text("Device Overview Screen")
}

@Preview(name = "DeviceOverviewScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewDeviceOverviewScreenLight() {
    MeAppTheme(themeMode = ThemeMode.LIGHT) {
        DeviceOverviewScreen()
    }
}

@Preview(name = "DeviceOverviewScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDeviceOverviewScreenDark() {
    MeAppTheme(themeMode = ThemeMode.DARK) {
        DeviceOverviewScreen()
    }
}
