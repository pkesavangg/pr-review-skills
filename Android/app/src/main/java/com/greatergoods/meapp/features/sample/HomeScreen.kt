package com.greatergoods.meapp.features.sample

import android.annotation.SuppressLint
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.core.navigation.TOP_LEVEL_ROUTES

/**
 * Home screen that provides a Scaffold with a BottomNavigationBar for top-level navigation.
 *
 * @param selectedRoute The currently selected top-level route.
 * @param onSelect Callback to select a top-level route.
 * @param content The content to display above the bottom navigation.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(
    selectedRoute: AppRoute,
    content: @Composable () -> Unit
) {
    val topLevelBackStack = LocalNavBackStack.current
    Scaffold(
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_ROUTES.forEach { topLevelRoute ->
                    NavigationBarItem(
                        selected = selectedRoute::class == topLevelRoute.route::class,
                        onClick = { topLevelBackStack.addTopLevel(topLevelRoute.route) },
                        icon = { Icon(topLevelRoute.icon, contentDescription = null) },
                        label = { Text(topLevelRoute.route::class.simpleName ?: "") }
                    )
                }
            }
        }
    ) { innerPadding ->
        content()
    }
}
