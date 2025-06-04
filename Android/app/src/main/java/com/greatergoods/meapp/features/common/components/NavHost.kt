package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
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
import com.greatergoods.meapp.features.common.viewmodel.AppViewModel
import com.greatergoods.meapp.features.common.viewmodel.NavigationViewmodel
import com.greatergoods.meapp.initEntries
import com.greatergoods.meapp.mainEntries
import com.greatergoods.meapp.productEntries
import com.greatergoods.meapp.core.logging.LogManager
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 */
@Composable
fun NavHost(
    topLevelBackStack: TopLevelBackStack<NavKey>,
    appViewModel: AppViewModel,
    logManager: LogManager
) {
    val navController = rememberNavController()
    val navViewModel: NavigationViewmodel = hiltViewModel()

    NavigationObserver(
        appViewModel.navigationService.navigationIntent,
        backStack,
    )
    NavDisplay(
        backStack = topLevelBackStack.backStack,
        onBack = { topLevelBackStack.removeLast() },
        entryProvider =
            entryProvider {
                initEntries(appViewModel, logManager)
                mainEntries()
                productEntries()
            },
    )
}
