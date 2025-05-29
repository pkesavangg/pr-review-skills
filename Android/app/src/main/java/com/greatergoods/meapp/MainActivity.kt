package com.greatergoods.meapp

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.rememberNavBackStack
import com.greatergoods.meapp.core.navigation.AppNavigation
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import com.greatergoods.meapp.theme.MeAppTheme
import dagger.hilt.android.AndroidEntryPoint

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = hiltViewModel<NavigationViewmodel>()
            val backStack = rememberNavBackStack(AppRoute.Init.Login)

            MeAppTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                AppRoute.Init.Login,
                                AppRoute.Scale,
                                AppRoute.History
                            ).forEach { route ->
                                val isSelected = route == backStack.last()
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { backStack.add(route) },
                                    label = {
                                        Text(route.toString())
                                    },
                                    icon = { },
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    AppNavigation(
                        backStack = backStack,
                        navigationIntent = viewModel.appEventService.navigationIntent,
                        onBack = { backStack.removeLast() }
                    )
                }
            }
        }
    }
}


