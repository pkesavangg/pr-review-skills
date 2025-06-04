package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.domain.interfaces.INavigationHandler
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for handling navigation events and intent-based navigation in the app.
 */
@HiltViewModel
class NavigationViewmodel @Inject constructor(private val appEventService: IAppEventService) : ViewModel(),
    INavigationHandler {

    /** Navigation state flow, emits navigation intents. */
    override val navigationState: Flow<NavigationIntent>?
        get() = appEventService.navigationIntent

    /**
     * Navigates to the specified route.
     * @param route The destination route.
     */
    override suspend fun navigateTo(route: AppRoute) {
        viewModelScope.launch {
            appEventService.navigateTo(route)
        }
    }

    /**
     * Navigates to multiple destinations in sequence.
     * @param destinations The list of routes to navigate to.
     */
    override suspend fun navigateTo(destinations: List<AppRoute>) {
        viewModelScope.launch {
            appEventService.navigateTo(destinations)
        }
    }

    /**
     * Navigates to the root of the navigation stack.
     * @param currentRoute The current route to set as root.
     */
    override suspend fun navigateToRoot() {
        viewModelScope.launch {
            appEventService.navigateToRoot()
        }
    }

    /**
     * Replaces the navigation stack with the provided destinations.
     * @param destinations The new stack of routes.
     */
    override suspend fun replaceStack(destinations: List<AppRoute>) {
        viewModelScope.launch {
            appEventService.replaceStack(destinations)
        }
    }

    /**
     * Adds a new top-level route to the navigation stack.
     * @param route The route to add.
     */
    override suspend fun addTopLevelRoute(route: AppRoute) {
        viewModelScope.launch {
            appEventService.addTopLevelRoute(route)
        }
    }

    /**
     * Navigates back to the previous route or a specific route if provided.
     * @param route The route to navigate back to (optional).
     * @param inclusive Whether to include the specified route in the pop.
     */
    override suspend fun navigateBack(
        route: AppRoute?,
        inclusive: Boolean
    ) {
        viewModelScope.launch {
            appEventService.navigateBack(route, inclusive)
        }
    }
}
