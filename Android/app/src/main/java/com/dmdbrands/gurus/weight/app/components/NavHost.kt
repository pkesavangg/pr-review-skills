package com.dmdbrands.gurus.weight.app.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
      // New screen slides in from the right over the old screen
      slideInHorizontally(
        initialOffsetX = { it }, // 'it' is the full width for a right-to-left slide
        animationSpec = tween(250, easing = FastOutSlowInEasing) // Shortened duration to 250ms
      ) + fadeIn(animationSpec = tween(150, delayMillis = 250)) togetherWith // Fade in during the slide

        // Old screen can optionally fade out quickly or just remain static to be covered
        // Removing the explicit fadeOut often makes it smoother
        ExitTransition.None // Or fadeOut(animationSpec = tween(150)) if you want the old screen to vanish
    },
    popTransitionSpec = {
      // Incoming screen (the one we're returning to) simply fades in
      fadeIn(
        animationSpec = tween(500, easing = LinearOutSlowInEasing) // Gentler fade
      ) togetherWith
        // Outgoing screen slides out to the right, slightly slower
        slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(500, easing = EaseOut) // Smoother slide out
        )
    },
    predictivePopTransitionSpec = {
      // Clean predictive back
      fadeIn(animationSpec = tween(150)) togetherWith
        slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(150))
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
