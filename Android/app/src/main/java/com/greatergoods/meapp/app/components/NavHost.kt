package com.greatergoods.meapp.app.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.example.nav3integration.TopLevelBackStack
import com.greatergoods.meapp.app.viewmodel.AppViewModel
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.NavigationObserver
import com.greatergoods.meapp.features.home.HomeScreen
import com.greatergoods.meapp.features.loading.LoadingScreen

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
        modifier = Modifier.navigationBarsPadding(),
        entryDecorators = listOf(
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
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
        entryDecorators = listOf(
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
        ),
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
