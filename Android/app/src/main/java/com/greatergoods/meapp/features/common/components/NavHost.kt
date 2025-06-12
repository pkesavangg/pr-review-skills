package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.nav3integration.TopLevelBackStack
import com.greatergoods.meapp.authEntries
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.NavigationObserver
import com.greatergoods.meapp.features.auth.AppViewModel
import com.greatergoods.meapp.features.sample.LoadingScreen
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
    val backStack by topLevelBackStack.backStack.collectAsState()

    NavigationObserver(
        appViewModel.navigationService.navigationIntent,
        topLevelBackStack,
    )
    NavDisplay(
        backStack = backStack,
        onBack = {
            topLevelBackStack.removeLast()
        },
        entryProvider =
            entryProvider {
                entry<AppRoute.Init.Loading> { LoadingScreen() }
                authEntries()
                topLevelEntries()
            },
    )
}
