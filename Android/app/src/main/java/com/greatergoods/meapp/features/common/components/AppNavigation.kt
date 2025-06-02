package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.NavigationObserver
import com.greatergoods.meapp.core.navigation.TopLevelBackStack
import com.greatergoods.meapp.features.theme.ThemeViewModel

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 */
@Composable
fun NavGraph(
    backStack: TopLevelBackStack<NavKey>,
    themeViewModel: ThemeViewModel,
) {
    NavigationObserver(
        themeViewModel.appEventService.navigationIntent,
        backStack,
    )
    val selectedRoute = backStack.topLevelKey as AppRoute
    NavDisplay(
        backStack = backStack.backStack,
        onBack = { backStack.removeLast() },
        entryProvider =
            entryProvider {
                initEntries(themeViewModel)
                mainEntries()
                productEntries()
            },
    )
}
