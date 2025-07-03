package com.greatergoods.meapp.core.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import com.example.nav3integration.TopLevelBackStack
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
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
                when (intent) {
                    is NavigationIntent.NavigateTo -> {
                        coroutineScope.launch {
                            backStack.addRoute(
                                intent.route,
                                intent.topLevel,
                                intent.popUpTo,
                            )
                        }
                    }

                    is NavigationIntent.NavigateBack -> {
                        coroutineScope.launch {
                            backStack.removeLast(intent.topLevel)
                        }
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
                        coroutineScope.launch {
                            backStack.replaceStack(intent.route, intent.topLevel)
                        }
                    }

                    is NavigationIntent.ReplaceStackSingle -> {
                        coroutineScope.launch {
                            backStack.replaceStack(listOf(intent.route), intent.topLevel)
                        }
                    }

                    is NavigationIntent.RegisterOnDeactivate -> {
                        coroutineScope.launch {
                            backStack.registerCanDeactivate(intent.route, intent.callback)
                        }
                    }

                    is NavigationIntent.UnregisterOnDeactivate -> {
                        coroutineScope.launch {
                            backStack.unregisterCanDeactivate(intent.route)
                        }
                    }
                }
            }
    }
}
