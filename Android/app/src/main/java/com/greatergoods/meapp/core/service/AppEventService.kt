package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.interfaces.INavigationUtility
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Service for managing navigation events and emitting navigation intents to be observed by the UI.
 * Implements [IAppEventService].
 */
class AppEventService : IAppEventService {
    private val _navigationIntent = MutableSharedFlow<NavigationIntent>(replay = 1)

    /**
     * Shared flow of navigation intents to be observed by the UI.
     */
    override val navigationIntent = _navigationIntent.asSharedFlow()

    /**
     * Emits a navigation intent to navigate to the specified route.
     * @param route The destination [AppRoute].
     */
    override suspend fun navigateTo(route: AppRoute, topLevel: AppRoute?, popUpTo: AppRoute?) {
        emitNavigationIntent(
            NavigationIntent.NavigateTo(
                route,
                topLevel = topLevel,
                popUpTo = popUpTo,
            ),
        )
    }

    /**
     * Emits a navigation intent to navigate back, optionally to a specific route.
     * @param route The route to navigate back to (optional).
     * @param inclusive Whether to include the specified route in the pop.
     */
    override suspend fun navigateBack(
        topLevel: AppRoute?,
    ) {
        emitNavigationIntent(
            NavigationIntent.NavigateBack(
                topLevel,
            ),
        )
    }

    override suspend fun replaceStack(
        route: AppRoute,
        topLevel: AppRoute?,
    ) {
        emitNavigationIntent(
            NavigationIntent.ReplaceStack(
                listOf(route),
                topLevel,
            ),
        )
    }

    override suspend fun replaceStack(
        route: List<AppRoute>,
        topLevel: AppRoute?,
    ) {
        emitNavigationIntent(
            NavigationIntent.ReplaceStack(
                route,
                topLevel,
            ),
        )
    }

    override suspend fun login() {
        emitNavigationIntent(
            NavigationIntent.Login,
        )
    }

    override suspend fun autoLogin() {
        emitNavigationIntent(
            NavigationIntent.AutoLogin,
        )
    }

    override suspend fun logout() {
        emitNavigationIntent(
            NavigationIntent.Logout,
        )
    }

    /**
     * Helper to emit a navigation intent to the shared flow.
     * @param intent The navigation intent to emit.
     */
    private suspend fun emitNavigationIntent(intent: NavigationIntent) {
        _navigationIntent.emit(intent)
    }
}

/**
 * Interface for app event services that manage navigation.
 * Extends [INavigationUtility].
 */
interface IAppEventService : INavigationUtility
