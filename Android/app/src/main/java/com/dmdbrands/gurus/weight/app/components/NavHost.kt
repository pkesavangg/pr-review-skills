package com.dmdbrands.gurus.weight.app.components

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.dmdbrands.gurus.weight.app.viewmodel.AppViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.NavigationObserver
import com.dmdbrands.gurus.weight.features.feedMessages.AppFeedMessagesScreen
import com.dmdbrands.gurus.weight.features.home.HomeScreen
import com.dmdbrands.gurus.weight.features.loading.LoadingScreen
import com.example.nav3integration.TopLevelBackStack
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
        entry<AppRoute.FeedMessages> {
          AppFeedMessagesScreen()
        }
        entry<AppRoute.FeedLanding> {
          com.dmdbrands.gurus.weight.features.feed.FeedLandingScreen(
            onNavigateBack = {
              coroutineScope.launch {
                topLevelBackStack.removeLast(AppRoute.App)
              }
            },
            onNavigateToProduct = { link, variationId ->
              // TODO: Handle product navigation
            },
            onNavigateToFeedLanding = { feedItem ->
              // TODO: Handle nested navigation if needed
            },
          )
        }
        entry<AppRoute.FeedFAQ> {
          com.dmdbrands.gurus.weight.features.feed.FeedFAQScreen(
            onNavigateBack = {
              coroutineScope.launch {
                topLevelBackStack.removeLast(AppRoute.App)
              }
            },
          )
        }
        authEntries()
        accountSettingsEntries()
        scaleDetailEntries()
        scaleSetupEntries()
        historyEntries()
        dashboardEntries()
        integrationEntries()
        feedMessagesEntries()
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
        rememberViewModelStoreNavEntryDecorator(),
      ),
    backStack = topLevelBackStack.getStackForTopLevel(AppRoute.Home),
    onBack = {
      coroutineScope.launch {
        topLevelBackStack.removeLast(AppRoute.Home)
      }
    },
    entryProvider =
      entryProvider {
        entry<AppRoute.Init.Loading> { LoadingScreen() }
        topLevelEntries()
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
