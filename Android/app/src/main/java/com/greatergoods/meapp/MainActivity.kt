package com.greatergoods.meapp

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.presentation.ui.LoginScreen
import com.greatergoods.meapp.theme.MeAppTheme
import dagger.hilt.android.AndroidEntryPoint
import android.os.Bundle

/**
 * Main activity that hosts the sample theme screen and demonstrates dynamic theming.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LoginScreen()
                }
            }
        }
    }
}

