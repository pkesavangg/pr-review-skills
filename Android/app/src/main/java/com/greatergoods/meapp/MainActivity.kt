package com.greatergoods.meapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.greatergoods.meapp.features.common.SampleThemeScreen
import com.greatergoods.meapp.theme.MeAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity that hosts the sample theme screen and demonstrates dynamic theming.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            MeAppTheme(darkTheme = isDarkTheme) {
                SampleThemeScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

