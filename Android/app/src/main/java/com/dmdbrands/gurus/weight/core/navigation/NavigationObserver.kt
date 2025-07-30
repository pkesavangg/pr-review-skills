package com.dmdbrands.gurus.weight.core.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import com.example.nav3integration.TopLevelBackStack
import com.dmdbrands.gurus.weight.domain.interfaces.NavigationIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Observes navigation intents and updates the navigation back stack accordingly.
 *
 * @param navigationIntentFlow The flow of navigation intents to observe.
 * @param backStack The top-level navigation back stack to update.
 */
@Composable
fun NavigationObserver(
  navigationIntentFlow: Flow<NavigationIntent>?,
  backStack: TopLevelBackStack<NavKey>,
) {
  val activity = LocalActivity.current
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(activity) {
    navigationIntentFlow
      ?.collect { intent ->
        coroutineScope.launch {

          when (intent) {
            is NavigationIntent.NavigateTo -> {
              backStack.addRoute(
                intent.route,
                intent.topLevel,
                intent.popUpTo,
              )
            }

            is NavigationIntent.NavigateBack -> {
              backStack.removeLast(intent.topLevel)
            }

            is NavigationIntent.GetCurrentRoute -> {
              intent.response.complete(backStack.currentRoute)
            }

            is NavigationIntent.Login -> {
              backStack.login()
            }

            is NavigationIntent.ReInitialize -> {
              backStack.reInitialize()
            }

            is NavigationIntent.AutoLogin -> {
              backStack.autoLogin()
            }

            is NavigationIntent.ReplaceStack -> {
              backStack.replaceStack(intent.route, intent.topLevel)
            }

            is NavigationIntent.ReplaceStackSingle -> {
              backStack.replaceStack(listOf(intent.route), intent.topLevel)
            }

            is NavigationIntent.RegisterOnDeactivate -> {
              backStack.registerCanDeactivate(intent.route, intent.callback)
            }

            is NavigationIntent.UnregisterOnDeactivate -> {
              backStack.unregisterCanDeactivate(intent.route)
            }
          }
        }
      }
  }
}
