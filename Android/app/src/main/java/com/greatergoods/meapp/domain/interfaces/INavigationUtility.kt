package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.core.navigation.AppRoute
import kotlinx.coroutines.flow.Flow

/**
 * Interface for navigation-related utilities.
 */
interface INavigationUtility {
    val navigationIntent: Flow<NavigationIntent>

    suspend fun navigateTo(route: AppRoute, topLevel: AppRoute? = null, popUpTo: AppRoute? = null)

    suspend fun navigateBack(
        topLevel: AppRoute? = null,
    )

    suspend fun login()

    suspend fun logout()

    suspend fun autoLogin()
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
        val topLevel: AppRoute? = null,
        val popUpTo: AppRoute? = null,
    ) : NavigationIntent

    data class NavigateBack(
        val topLevel: AppRoute? = null,
    ) : NavigationIntent

    data object Login : NavigationIntent
    data object Logout : NavigationIntent
    data object AutoLogin : NavigationIntent
}
