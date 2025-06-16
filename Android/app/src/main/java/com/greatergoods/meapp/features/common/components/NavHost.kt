package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.nav3integration.TopLevelBackStack
import com.greatergoods.meapp.authEntries
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.NavigationObserver
import com.greatergoods.meapp.features.auth.viewmodel.AppViewModel
import com.greatergoods.meapp.features.home.HomeScreen
import com.greatergoods.meapp.features.auth.LoadingScreen
import com.greatergoods.meapp.topLevelEntries

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 */
@Composable
fun NavHost(
    topLevelBackStack: TopLevelBackStack<NavKey>,
    appViewModel: AppViewModel,
) {

    NavigationObserver(
        appViewModel.navigationService.navigationIntent,
        topLevelBackStack,
    )
    NavDisplay(
        backStack = topLevelBackStack.getStackForTopLevel(AppRoute.App),
        onBack = {
            topLevelBackStack.removeLast(AppRoute.App)
        },
        entryProvider =
            entryProvider {
                entry<AppRoute.Init.Loading> { LoadingScreen() }
                entry<AppRoute.Home> { HomeScreen() }
                authEntries()
            },
    )
}

@Composable
fun HomeNavHost(
    topLevelBackStack: TopLevelBackStack<NavKey>,
) {
    NavDisplay(
        backStack = topLevelBackStack.getStackForTopLevel(AppRoute.Home),
        onBack = {
            topLevelBackStack.removeLast(AppRoute.Home)
        },
        entryProvider =
            entryProvider {
                topLevelEntries()
            },
    )
}
