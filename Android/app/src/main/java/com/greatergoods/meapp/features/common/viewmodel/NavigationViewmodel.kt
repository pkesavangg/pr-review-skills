package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.IAppEventService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Intent

/**
 * ViewModel for handling navigation events and intent-based navigation in the app.
 */
@HiltViewModel
open class NavigationViewmodel @Inject constructor() : ViewModel() {

    @Inject
    lateinit var appEventService: IAppEventService

    private val _startDestination = MutableStateFlow<AppRoute>(AppRoute.Init.SampleScreen)
    val startDestination: StateFlow<AppRoute> = _startDestination

    /**
     * Handles navigation based on the provided intent.
     * @param intent The intent to parse for navigation destination.
     */
    fun handleIntent(intent: Intent?) {
        val destination = intent?.getStringExtra("destination")
        val route = when (destination) {
            "productDetail" -> AppRoute.Product.ProductList
            // Add more cases as needed
            else -> AppRoute.Init.SampleScreen
        }
        _startDestination.value = route
    }

    /**
     * Navigates to the specified route.
     * @param route The destination route.
     */
    fun navigateTo(
        route: AppRoute,
    ) {
        viewModelScope.launch {
            appEventService.navigateTo(route)
        }
    }

    /**
     * Navigates back to the previous route or a specific route if provided.
     * @param route The route to navigate back to (optional).
     * @param inclusive Whether to include the specified route in the pop.
     */
    fun navigateBack(
        route: AppRoute? = null,
        inclusive: Boolean = false,
    ) {
        viewModelScope.launch {
            appEventService.navigateBack(route, inclusive)
        }
    }

    /**
     * Navigates to multiple destinations in sequence.
     * @param destinations The list of routes to navigate to.
     */
    fun navigateTo(
        destinations: List<AppRoute>,
    ) {
        viewModelScope.launch {
            appEventService.navigateTo(destinations)
        }
    }

    /**
     * Replaces the navigation stack with the provided destinations.
     * @param destinations The new stack of routes.
     */
    fun replaceStack(
        destinations: List<AppRoute>,
    ) {
        viewModelScope.launch {
            appEventService.replaceStack(destinations)
        }
    }

    /**
     * Navigates to the root of the navigation stack.
     * @param currentRoute The current route to set as root.
     */
    fun navigateToRoot(
        currentRoute: AppRoute,
    ) {
        viewModelScope.launch {
            appEventService.navigateToRoot(currentRoute)
        }
    }
}
