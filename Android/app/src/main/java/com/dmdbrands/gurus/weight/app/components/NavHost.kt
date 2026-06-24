package com.dmdbrands.gurus.weight.app.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
        rememberSaveableStateHolderNavEntryDecorator(),
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
        initialOffsetX = { it },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
      ) + fadeIn(animationSpec = tween(150, delayMillis = 200)) togetherWith

        ExitTransition.None
    },
    popTransitionSpec = {
      // Clean slide-out: old screen slides out, previous screen fades in
      fadeIn(animationSpec = tween(300)) togetherWith
        slideOutHorizontally(
          targetOffsetX = { it },
          animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(100))
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
        rememberSaveableStateHolderNavEntryDecorator(),
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
      val isSnapshotToDashboard = initialState is AppRoute.Main.DashboardSnapshot &&
        targetState is AppRoute.Main.Dashboard
      if (isSnapshotToDashboard) {
        // Snapshot → Dashboard: scale up + fade in
        scaleIn(
          initialScale = 0.92f,
          animationSpec = tween(350, easing = FastOutSlowInEasing),
        ) + fadeIn(animationSpec = tween(250, delayMillis = 100)) togetherWith
          fadeOut(animationSpec = tween(200))
      } else {
        ContentTransform(
          fadeIn(animationSpec = tween(0)),
          fadeOut(animationSpec = tween(0)),
        )
      }
    },
    popTransitionSpec = {
      val isDashboardToSnapshot = initialState is AppRoute.Main.Dashboard &&
        targetState is AppRoute.Main.DashboardSnapshot
      if (isDashboardToSnapshot) {
        // Dashboard → Snapshot: fade in + scale down exit
        fadeIn(animationSpec = tween(250)) togetherWith
          scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
          ) + fadeOut(animationSpec = tween(200))
      } else {
        ContentTransform(
          fadeIn(animationSpec = tween(0)),
          fadeOut(animationSpec = tween(0)),
        )
      }
    },
    predictivePopTransitionSpec = {
      val isDashboardToSnapshot = initialState is AppRoute.Main.Dashboard &&
        targetState is AppRoute.Main.DashboardSnapshot
      if (isDashboardToSnapshot) {
        fadeIn(animationSpec = tween(200)) togetherWith
          scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
          ) + fadeOut(animationSpec = tween(150))
      } else {
        ContentTransform(
          fadeIn(animationSpec = tween(0)),
          fadeOut(animationSpec = tween(0)),
        )
      }
    },
  )
}
