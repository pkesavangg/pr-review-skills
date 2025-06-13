package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for handling navigation events and intent-based navigation in the app.
 */
@HiltViewModel
class NavigationViewmodel
@Inject
constructor(
    private val appEventService: IAppEventService,
) : ViewModel() {
    /** Navigation state flow, emits navigation intents. */
    val navigationState: Flow<NavigationIntent>?
        get() = appEventService.navigationIntent

    /**
     * Navigates to the specified route.
     * @param route The destination route.
     */
    fun navigateTo(route: AppRoute, topLevel: AppRoute? = null, popUpTo: AppRoute? = null) {
        viewModelScope.launch {
            AppLog.i("NavigationViewModel", "Navigating to route", "Route: $route")
            appEventService.navigateTo(route, topLevel, popUpTo)
        }
    }

    /**
     * Navigates back to the previous route or a specific route if provided.
     * @param route The route to navigate back to (optional).
     * @param inclusive Whether to include the specified route in the pop.
     */
    fun navigateBack(
        topLevel: AppRoute?,
    ) {
        viewModelScope.launch {
            appEventService.navigateBack(topLevel)
        }
    }
}
