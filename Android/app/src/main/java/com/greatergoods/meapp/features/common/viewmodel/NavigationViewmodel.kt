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
class NavigationViewmodel @Inject constructor() : ViewModel(), INavigationHandler {

    /** App event service for navigation, must be initialized before use. */
    lateinit var appEventService: IAppEventService
        private set

    /** Navigation state flow, emits navigation intents. */
    override var navigationState: Flow<NavigationIntent>? = null
        private set

    /**
     * Initializes the app event service. Must be called before using navigation methods.
     * @param service The IAppEventService instance to use.
     */
    fun initAppEventService(service: IAppEventService) {
        appEventService = service
        navigationState = appEventService.navigationIntent
    }

    /**
     * Navigates to the specified route.
     * @param route The destination route.
     */
    override suspend fun navigateTo(route: AppRoute) {
        if (::appEventService.isInitialized) {
            viewModelScope.launch {
                appEventService.navigateTo(route)
            }
        }
    }

    /**
     * Navigates to multiple destinations in sequence.
     * @param destinations The list of routes to navigate to.
     */
    override suspend fun navigateTo(destinations: List<AppRoute>) {
        if (::appEventService.isInitialized) {
            viewModelScope.launch {
                appEventService.navigateTo(destinations)
            }
        }
    }

    /**
     * Navigates to the root of the navigation stack.
     * @param currentRoute The current route to set as root.
     */
    override suspend fun navigateToRoot(currentRoute: AppRoute) {
        if (::appEventService.isInitialized) {
            viewModelScope.launch {
                appEventService.navigateToRoot(currentRoute)
            }
        }
    }

    /**
     * Replaces the navigation stack with the provided destinations.
     * @param destinations The new stack of routes.
     */
    override suspend fun replaceStack(destinations: List<AppRoute>) {
        if (::appEventService.isInitialized) {
            viewModelScope.launch {
                appEventService.replaceStack(destinations)
            }
        }
    }

    /**
     * Adds a new top-level route to the navigation stack.
     * @param route The route to add.
     */
    override suspend fun addTopLevelRoute(route: AppRoute) {
        if (::appEventService.isInitialized) {
            viewModelScope.launch {
                appEventService.addTopLevelRoute(route)
            }
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
        if (::appEventService.isInitialized) {
            viewModelScope.launch {
                appEventService.navigateBack(route, inclusive)
            }
        }
    }
}
