package com.greatergoods.meapp.app.components

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import com.greatergoods.meapp.features.historyDetail.HistoryDetailScreen
import com.greatergoods.meapp.features.home.HomeScreen
import com.greatergoods.meapp.features.loading.LoadingScreen
import kotlinx.coroutines.launch

/**
 * Main navigation composable for the app, handling top-level navigation and back stack management.
 *
 */
@Composable
fun NavHost(
    topLevelBackStack: TopLevelBackStack<NavKey>,
    appViewModel: AppViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    NavigationObserver(
        appViewModel.navigationService.navigationIntent,
        topLevelBackStack,
    )
    val backStack = topLevelBackStack.topLevelStacks.collectAsState()
    NavDisplay(
        modifier = Modifier.navigationBarsPadding(),
        entryDecorators =
            listOf(
                rememberSceneSetupNavEntryDecorator(),
                rememberSavedStateNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        backStack = topLevelBackStack.getStackForTopLevel(AppRoute.App),
        onBack = {
            coroutineScope.launch {
                topLevelBackStack.removeLast(AppRoute.App)
            }
        },
        entryProvider =
            entryProvider {
                entry<AppRoute.Init.Loading> { LoadingScreen() }
                entry<AppRoute.Home> { HomeScreen() }
                authEntries()
                accountSettingsEntries()
                entry<AppRoute.MonthDetails> { entry ->
                    HistoryDetailScreen(entry.month)
                }
            },
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
    )
}

@Composable
fun HomeNavHost(topLevelBackStack: TopLevelBackStack<NavKey>) {
    val coroutineScope = rememberCoroutineScope()
    NavDisplay(
        entryDecorators =
            listOf(
                rememberSceneSetupNavEntryDecorator(),
                rememberSavedStateNavEntryDecorator(),
            ),
        backStack = topLevelBackStack.getStackForTopLevel(AppRoute.Home),
        onBack = {
            coroutineScope.launch {
                topLevelBackStack.removeLast(AppRoute.Home)
            }
        },
        entryProvider =
            entryProvider {
                topLevelEntries()
                accountSettingsEntries()
            },
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
    )
}
