package com.greatergoods.meapp.features.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.service.IAppEventService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
open class NavigationViewmodel @Inject constructor() : ViewModel() {

    @Inject
    lateinit var appEventService: IAppEventService

    fun navigateTo(
        route: AppRoute,
    ) {
        viewModelScope.launch {
            appEventService.navigateTo(route)
        }
    }

    fun navigateBack(
        route: AppRoute? = null,
        inclusive: Boolean = false,
    ) {
        viewModelScope.launch {
            appEventService.navigateBack(route, inclusive)
        }
    }

    fun navigateTo(
        destinations: List<AppRoute>,
    ) {
        viewModelScope.launch {
            appEventService.navigateTo(destinations)
        }
    }

    fun replaceStack(
        destinations: List<AppRoute>,
    ) {
        viewModelScope.launch {
            appEventService.replaceStack(destinations)
        }
    }

    fun navigateToRoot(
        currentRoute: AppRoute,
    ) {
        viewModelScope.launch {
            appEventService.navigateToRoot(currentRoute)
        }
    }
}