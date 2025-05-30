package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.domain.interfaces.INavigationUtility
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppEventService() : IAppEventService {
    private val _navigationIntent = MutableSharedFlow<NavigationIntent>(replay = 1)

    override val navigationIntent = _navigationIntent.asSharedFlow()

    override suspend fun navigateTo(
        route: AppRoute,
    ) {
        emitNavigationIntent(
            NavigationIntent.NavigateTo(
                route,
            ),
        )
    }

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

    override suspend fun navigateToRoot(currentRoute: AppRoute) {
        emitNavigationIntent(
            NavigationIntent.NavigateToRoot(currentRoute),
        )
    }

    override suspend fun navigateTo(destinations: List<AppRoute>) {
        emitNavigationIntent(
            NavigationIntent.NavigateToMultiple(destinations),
        )
    }

    override suspend fun replaceStack(destinations: List<AppRoute>) {
        emitNavigationIntent(
            NavigationIntent.ReplaceStack(destinations),
        )
    }

    override suspend fun addTopLevelRoute(route: AppRoute) {
        emitNavigationIntent(
            NavigationIntent.AddTopLevelRoute(route),
        )
    }

    private suspend fun emitNavigationIntent(intent: NavigationIntent) {
        _navigationIntent.emit(intent)
    }
}

interface IAppEventService : INavigationUtility
