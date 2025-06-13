package com.greatergoods.meapp.core.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import com.example.nav3integration.TopLevelBackStack
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import kotlinx.coroutines.flow.Flow

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

    LaunchedEffect(activity) {
        navigationIntentFlow
            ?.collect { intent ->
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

                    is NavigationIntent.Login -> {
                        backStack.login()
                    }

                    is NavigationIntent.Logout -> {
                        backStack.logout()
                    }

                    is NavigationIntent.AutoLogin -> {
                        backStack.autoLogin()
                    }
                }
            }
    }
}
