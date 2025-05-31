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
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.AppNavigation
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import com.greatergoods.meapp.theme.MeAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity that hosts the sample theme screen and demonstrates dynamic theming.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = hiltViewModel<NavigationViewmodel>()

            MeAppTheme {
                AppNavigation(navigationViewModel = viewModel)
            }
        }
    }
}


