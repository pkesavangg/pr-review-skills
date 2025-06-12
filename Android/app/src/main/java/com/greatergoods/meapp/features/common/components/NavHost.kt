package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.greatergoods.meapp.authEntries
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.NavigationObserver
import com.greatergoods.meapp.core.navigation.TopLevelBackStack
import com.greatergoods.meapp.features.auth.AppViewModel
import com.greatergoods.meapp.features.sample.LoadingScreen
import com.greatergoods.meapp.homeEntries

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
        backStack = topLevelBackStack.backStack,
        onBack = { topLevelBackStack.removeLast() },
        entryProvider =
            entryProvider {
                entry<AppRoute.Init.Loading> { LoadingScreen() }
                authEntries()
                homeEntries()
            },
    )
}
