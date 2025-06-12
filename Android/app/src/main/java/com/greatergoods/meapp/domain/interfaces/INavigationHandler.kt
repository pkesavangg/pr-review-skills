package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.core.navigation.AppRoute
import kotlinx.coroutines.flow.Flow

/**
 * Interface for handling navigation in the app.
 */
interface INavigationHandler {
    /**
     * Get the current navigation state as a StateFlow
     */
    val navigationState: Flow<NavigationIntent>?

    /**
     * Navigate to a single route
     */
    suspend fun navigateTo(route: AppRoute)

    /**
     * Navigate to multiple routes
     */
    suspend fun navigateTo(destinations: List<AppRoute>)

    /**
     * Navigate to the root route
     */
    suspend fun navigateToRoot()

    /**
     * Add a new top-level route
     */
    suspend fun addTopLevelRoute(route: AppRoute)

    /**
     * Navigate back to a specific route
     */
    suspend fun navigateBack(route: AppRoute? = null, inclusive: Boolean = false)
}
