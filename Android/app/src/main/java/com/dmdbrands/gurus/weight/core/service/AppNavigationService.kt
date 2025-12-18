package com.dmdbrands.gurus.weight.core.service

import androidx.navigation3.runtime.NavKey
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.domain.interfaces.INavigationUtility
import com.dmdbrands.gurus.weight.domain.interfaces.NavigationIntent
import com.dmdbrands.gurus.weight.domain.services.AuthState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Service for managing navigation events and emitting navigation intents to be observed by the UI.
 * Implements [IAppNavigationService].
 */
class AppNavigationService : IAppNavigationService {
  // replay = 0 prevents old intents from being replayed on app restart after finishAffinity
  private val _navigationIntent = MutableSharedFlow<NavigationIntent>()

  // Auth event flow for authentication events (login, logout, etc.)
  private val _authEvent = MutableSharedFlow<AuthState>(replay = 1)
  override val authEvent: SharedFlow<AuthState> = _authEvent.asSharedFlow()

  /**
   * Shared flow of navigation intents to be observed by the UI.
   */
  override val navigationIntent = _navigationIntent.asSharedFlow()

  override suspend fun getCurrentRoute(): NavKey? {
    val response = CompletableDeferred<NavKey?>()
    emitNavigationIntent(NavigationIntent.GetCurrentRoute(response))
    return response.await()
  }

  /**
   * Emits a navigation intent to navigate to the specified route.
   * @param route The destination [AppRoute].
   */
  override suspend fun navigateTo(
    route: AppRoute,
    topLevel: AppRoute?,
    popUpTo: AppRoute?,
  ) {
    emitNavigationIntent(
      NavigationIntent.NavigateTo(
        route,
        topLevel = topLevel,
        popUpTo = popUpTo,
      ),
    )
  }

  /**
   * Emits a navigation intent to navigate back, optionally to a specific route.
   * @param route The route to navigate back to (optional).
   * @param inclusive Whether to include the specified route in the pop.
   */
  override suspend fun navigateBack(topLevel: AppRoute?) {
    emitNavigationIntent(
      NavigationIntent.NavigateBack(
        topLevel,
      ),
    )
  }

  /**
   * Emits a navigation intent to replace the last route and navigate to a new one.
   * @param route The route to navigate to.
   * @param topLevel The top-level key.
   */
  override suspend fun replaceLastAndNavigate(route: AppRoute, topLevel: AppRoute?) {
    emitNavigationIntent(
      NavigationIntent.ReplaceLastAndNavigate(
        route,
        topLevel,
      ),
    )
  }

  override suspend fun replaceStack(
    route: AppRoute,
    topLevel: AppRoute?,
  ) {
    emitNavigationIntent(
      NavigationIntent.ReplaceStack(
        listOf(route),
        topLevel,
      ),
    )
  }

  override suspend fun replaceStack(
    route: List<AppRoute>,
    topLevel: AppRoute?,
  ) {
    emitNavigationIntent(
      NavigationIntent.ReplaceStack(
        route,
        topLevel,
      ),
    )
  }

  override suspend fun login() {
    emitNavigationIntent(
      NavigationIntent.Login,
    )
  }

  override suspend fun autoLogin() {
    emitNavigationIntent(
      NavigationIntent.AutoLogin,
    )
  }

  override suspend fun reInitialize() {
    emitNavigationIntent(
      NavigationIntent.ReInitialize,
    )
  }

  override suspend fun emitAuthEvent(state: AuthState) {
    _authEvent.emit(state)
  }

  /**
   * Helper to emit a navigation intent to the shared flow.
   * @param intent The navigation intent to emit.
   */
  private suspend fun emitNavigationIntent(intent: NavigationIntent) {
    _navigationIntent.emit(intent)
  }

  /**
   * Registers an onDeactivate callback for a route. The callback is invoked before navigating away from the route.
   * @param route The route to guard.
   * @param callback The suspendable callback to invoke before navigation away.
   */
  override suspend fun registerOnDeactivate(
    route: NavKey,
    callback: suspend () -> Boolean,
  ) {
    _navigationIntent.emit(NavigationIntent.RegisterOnDeactivate(route, callback))
  }

  /**
   * Unregisters the onDeactivate callback for a route.
   * @param route The route to remove the guard from.
   */
  override suspend fun unregisterOnDeactivate(route: NavKey) {
    _navigationIntent.emit(NavigationIntent.UnregisterOnDeactivate(route))
  }
}

/**
 * Interface for app event services that manage navigation.
 * Extends [INavigationUtility].
 */
interface IAppNavigationService : INavigationUtility {
  val authEvent: SharedFlow<AuthState>

  suspend fun emitAuthEvent(state: AuthState)
}
