package com.dmdbrands.gurus.weight.domain.interfaces

import androidx.navigation3.runtime.NavKey
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * Interface for navigation-related utilities.
 */
interface INavigationUtility {
  val navigationIntent: Flow<NavigationIntent>

  suspend fun getCurrentRoute(): NavKey?

  suspend fun navigateTo(
    route: AppRoute,
    topLevel: AppRoute? = null,
    popUpTo: AppRoute? = null,
  )

  suspend fun replaceStack(
    route: AppRoute,
    topLevel: AppRoute? = null,
  )

  suspend fun replaceStack(
    route: List<AppRoute>,
    topLevel: AppRoute? = null,
  )

  suspend fun navigateBack(topLevel: AppRoute? = null)

  suspend fun login()

  suspend fun reInitialize()

  suspend fun autoLogin()

  /**
   * Registers an onDeactivate callback for a route. The callback is invoked before navigating away from the route.
   * @param route The route to guard.
   * @param callback The suspendable callback to invoke before navigation away.
   */
  suspend fun registerOnDeactivate(
    route: NavKey,
    callback: suspend () -> Boolean,
  )

  /**
   * Unregisters the onDeactivate callback for a route.
   * @param route The route to remove the guard from.
   */
  suspend fun unregisterOnDeactivate(route: NavKey)
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

  data class ReplaceStack(
    val route: List<AppRoute>,
    val topLevel: AppRoute? = null,
  ) : NavigationIntent

  data class ReplaceStackSingle(
    val route: AppRoute,
    val topLevel: AppRoute? = null,
  ) : NavigationIntent

  data class RegisterOnDeactivate(
    val route: NavKey,
    val callback: suspend () -> Boolean,
  ) : NavigationIntent

  data class UnregisterOnDeactivate(
    val route: NavKey,
  ) : NavigationIntent

  // ✅ New intent for requesting current route
  data class GetCurrentRoute(
    val response: CompletableDeferred<NavKey?>
  ) : NavigationIntent

  data object Login : NavigationIntent

  data object ReInitialize : NavigationIntent

  data object AutoLogin : NavigationIntent
}
