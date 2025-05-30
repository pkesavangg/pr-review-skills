package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.core.navigation.AppRoute
import kotlinx.coroutines.flow.Flow

/**
 * Interface for navigation-related utilities.
 */
interface INavigationUtility {
    val navigationIntent: Flow<NavigationIntent>

    suspend fun navigateTo(
        route: AppRoute,
    )

    suspend fun navigateBack(
        route: AppRoute? = null,
        inclusive: Boolean = false,
    )

    suspend fun navigateToRoot(currentRoute: AppRoute)
    suspend fun navigateTo(destinations: List<AppRoute>)

    suspend fun replaceStack(destinations: List<AppRoute>)
    suspend fun addTopLevelRoute(route: AppRoute)
}

/**
 * Represents navigation intents for the app.
 */
sealed interface NavigationIntent {

    /**
     * Represents a navigation action to a specific route.
     *
     * @property route The destination route to navigate to.
     */

    data class NavigateTo(
        val route: AppRoute,
    ) : NavigationIntent

    data class NavigateBack(
        val route: AppRoute? = null,
        val inclusive: Boolean = false,
    ) : NavigationIntent

    data class NavigateToRoot(
        val currentRoute: AppRoute,
    ) : NavigationIntent

    data class NavigateToMultiple(
        val routes: List<AppRoute>,
    ) : NavigationIntent

    data class ReplaceStack(
        val routes: List<AppRoute>,
    ) : NavigationIntent

    data class AddTopLevelRoute(
        val route: AppRoute,
    ) : NavigationIntent
}


