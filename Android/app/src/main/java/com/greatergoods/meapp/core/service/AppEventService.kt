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
    override suspend fun navigateTo(route: AppRoute) {
        emitNavigationIntent(
            NavigationIntent.NavigateTo(
                route,
            ),
        )
    }

    /**
     * Emits a navigation intent to navigate back, optionally to a specific route.
     * @param route The route to navigate back to (optional).
     * @param inclusive Whether to include the specified route in the pop.
     */
    override suspend fun navigateBack(
        route: AppRoute?,
        inclusive: Boolean,
    ) {
        emitNavigationIntent(
            NavigationIntent.NavigateBack(
                route,
                inclusive,
            ),
        )
    }

    /**
     * Emits a navigation intent to navigate to the root of the stack.
     * @param currentRoute The current route.
     */
    override suspend fun navigateToRoot() {
        emitNavigationIntent(
            NavigationIntent.NavigateToRoot,
        )
    }

    /**
     * Emits a navigation intent to navigate to multiple destinations.
     * @param destinations The list of routes to navigate to.
     */
    override suspend fun navigateTo(destinations: List<AppRoute>) {
        emitNavigationIntent(
            NavigationIntent.NavigateToMultiple(destinations),
        )
    }

    /**
     * Emits a navigation intent to replace the current stack with the given destinations.
     * @param destinations The new stack of routes.
     */
    override suspend fun replaceStack(destinations: List<AppRoute>) {
        emitNavigationIntent(
            NavigationIntent.ReplaceStack(destinations),
        )
    }

    /**
     * Emits a navigation intent to add a top-level route.
     * @param route The top-level route to add.
     */
    override suspend fun addTopLevelRoute(route: AppRoute) {
        emitNavigationIntent(
            NavigationIntent.AddTopLevelRoute(route),
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
