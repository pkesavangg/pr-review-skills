package com.greatergoods.meapp.features.sample

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Displays the My Scales screen.
 */
@Composable
fun MyScalesScreen() {
    Text("My Scales Screen")
}

@PreviewTheme
@Composable
private fun PreviewMyScalesScreen() {
    MeAppTheme {
        MyScalesScreen()
    }
}
