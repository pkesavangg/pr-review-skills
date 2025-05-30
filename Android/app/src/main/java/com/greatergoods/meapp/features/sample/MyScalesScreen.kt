package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays the My Scales screen.
 */
@Composable
fun MyScalesScreen() {
    Text("My Scales Screen")
}

@Preview(name = "MyScalesScreen Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewMyScalesScreenLight() {
    MeAppTheme(darkTheme = false) {
        MyScalesScreen()
    }
}

@Preview(name = "MyScalesScreen Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewMyScalesScreenDark() {
    MeAppTheme(darkTheme = true) {
        MyScalesScreen()
    }
} 