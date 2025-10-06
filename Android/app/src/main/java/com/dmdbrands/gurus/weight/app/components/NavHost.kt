package com.dmdbrands.gurus.weight.app.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.dmdbrands.gurus.weight.app.viewmodel.AppViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.NavigationObserver
import com.dmdbrands.gurus.weight.features.home.HomeScreen
import com.dmdbrands.gurus.weight.features.loading.LoadingScreen
import com.example.nav3integration.TopLevelBackStack

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
    entryDecorators =
      listOf(
        rememberSceneSetupNavEntryDecorator(),
        rememberSavedStateNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
    backStack = topLevelBackStack.getStackForTopLevel(AppRoute.App).ifEmpty {
      listOf(
        AppRoute.Init.Loading,
      )
    },
    onBack = {
      topLevelBackStack.removeLastSync(AppRoute.App)
    },
    entryProvider =
      entryProvider {
        entry<AppRoute.Init.Loading> { LoadingScreen() }
        entry<AppRoute.Home> { HomeScreen() }
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
  NavDisplay(
    entryDecorators =
      listOf(
        rememberSceneSetupNavEntryDecorator(),
        rememberSavedStateNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
    backStack = topLevelBackStack.getStackForTopLevel(AppRoute.Home),
    onBack = {
      topLevelBackStack.removeLastSync(AppRoute.Home)
    },
    entryProvider =
      entryProvider {
        entry<AppRoute.Init.Loading> { LoadingScreen() }
        topLevelEntries()
      },
    transitionSpec = {
      ContentTransform(
        fadeIn(animationSpec = tween(0)),
        fadeOut(animationSpec = tween(0)),
      )
    },
    popTransitionSpec = {
      ContentTransform(
        fadeIn(animationSpec = tween(0)),
        fadeOut(animationSpec = tween(0)),
      )
    },
    predictivePopTransitionSpec = {
      ContentTransform(
        fadeIn(animationSpec = tween(0)),
        fadeOut(animationSpec = tween(0)),
      )
    },
  )
}
