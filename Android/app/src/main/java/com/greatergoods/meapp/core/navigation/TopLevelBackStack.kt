package com.example.nav3integration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.navigation3.runtime.NavKey
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

interface PublicRoute

class TopLevelBackStack<T : NavKey>(
  private val startKey: Pair<T, T>,
  private val loginKey: T,
  private val initialKey: Pair<T, T> = startKey,
) {
  private var onLoginSuccessRoute: Pair<T, T>? = null
  var isLoggedIn by mutableStateOf(false)
    private set

  private val _topLevelStacks =
    MutableStateFlow(
      linkedMapOf<T, SnapshotStateList<T>>(
        startKey.first to mutableStateListOf(startKey.second),
      ),
    )
  val topLevelStacks: StateFlow<LinkedHashMap<T, SnapshotStateList<T>>> = _topLevelStacks.asStateFlow()
  private val _currentDeepRoute = MutableStateFlow<T?>(null)
  val currentRoute: T?
    get() {
      val route: T = _topLevelStacks.value[startKey.first]?.lastOrNull() ?: return null
      return route
    }

  /**
   * Map of route to canDeactivate callback.
   * The callback returns true if navigation is allowed, false otherwise.
   */
  private val canDeactivateCallbacks = ConcurrentHashMap<T, suspend () -> Boolean>()
  private val canDeactivateMutex = Mutex()

  /**
   * Register a canDeactivate callback for a route.
   * @param route The route to guard.
   * @param callback The suspendable callback to invoke before navigation away.
   */
  fun registerCanDeactivate(
    route: T,
    callback: suspend () -> Boolean,
  ) {
    canDeactivateCallbacks[route] = callback
  }

  /**
   * Unregister the canDeactivate callback for a route.
   * @param route The route to remove the guard from.
   */
  fun unregisterCanDeactivate(route: T) {
    canDeactivateCallbacks.remove(route)
  }

  /**
   * Checks if navigation away from the given route is allowed by invoking its canDeactivate callback if present.
   * @param route The route to check.
   * @return true if navigation is allowed, false otherwise.
   */
  suspend fun canDeactivate(route: T): Boolean {
    val callback = canDeactivateCallbacks[route]
    return if (callback != null) {
      canDeactivateMutex.withLock { callback() }
    } else {
      true
    }
  }

  /**
   * Adds a route to the stack, checking canDeactivate if popUpTo is set and the stack is not empty.
   * @param route The route to add.
   * @param topLevel The top-level key.
   * @param popUpTo If set, clears the stack up to this route after canDeactivate check.
   */
  suspend fun addRoute(
    route: T,
    topLevel: T? = null,
    popUpTo: T? = null,
  ) {
    val topLevelKey = topLevel ?: startKey.first
    val stacks = _topLevelStacks.value
    val stack = stacks.getOrPut(topLevelKey) { mutableStateListOf() }

    if (popUpTo != null && stack.isNotEmpty()) {
      val currentRoute = stack.lastOrNull()
      if (currentRoute != null && !canDeactivate(currentRoute)) return
      stack.clear()
      stack.add(popUpTo)
    }
    if (popUpTo != route) {
      if (requiresLogin(route)) {
        onLoginSuccessRoute = Pair(topLevelKey, route)
        stack.add(loginKey)
      } else {
        stack.add(route)
      }
    }
  }

  /**
   * Replaces the stack with the given routes, checking canDeactivate for the current route first.
   * @param routes The new stack of routes.
   * @param topLevel The top-level key.
   */
  suspend fun replaceStack(
    routes: List<T>,
    topLevel: T? = null,
  ) {
    val topLevelKey = topLevel ?: startKey.first
    val stacks = _topLevelStacks.value
    val stack = stacks.getOrPut(topLevelKey) { mutableStateListOf() }
    val currentRoute = stack.lastOrNull()
    if (currentRoute != null && !canDeactivate(currentRoute)) return
    routes.forEachIndexed { index, route ->
      if (requiresLogin(route)) {
        onLoginSuccessRoute = Pair(topLevelKey, route)
        stack.add(index, loginKey)
      } else {
        stack.add(index, route)
      }
    }
    delay(100)
    stack.removeAll { it !in routes }
  }

  /**
   * Removes the last route from the stack, checking canDeactivate for the current route first.
   * @param topLevel The top-level key.
   */
  suspend fun removeLast(topLevel: T? = null) {
    val topLevelKey = topLevel ?: startKey.first
    val stacks = _topLevelStacks.value
    val stack = stacks[topLevelKey] ?: return
    val currentRoute = stack.lastOrNull() ?: return
    if (!canDeactivate(currentRoute)) return
    if (stack.isNotEmpty()) {
      stack.removeLastOrNull()
      if (stack.isEmpty()) {
        stacks.remove(topLevelKey)
      }
    }
  }

  fun getStackForTopLevel(topLevel: T): SnapshotStateList<T> = _topLevelStacks.value[topLevel] ?: mutableStateListOf()

  fun login() {
    isLoggedIn = true
    val target = onLoginSuccessRoute
    onLoginSuccessRoute = null

    target?.let { (stackKey, newRoute) ->
      val stacks = _topLevelStacks.value
      val stack = stacks[stackKey] ?: return
      val loginIndex = stack.indexOfLast { it == loginKey }
      if (loginIndex >= 0) {
        stack.removeAt(loginIndex)
        stack.add(newRoute)
      }
    }
  }

  suspend fun autoLogin() {
    isLoggedIn = true
    val stacks = _topLevelStacks.value
    val stack = stacks.getOrPut(startKey.first) { mutableStateListOf() }

    stack.clear()
    stack.add(initialKey.first)

    addRoute(initialKey.second, initialKey.first)
    AppLog.i("autoLogin", "Navigation to dashboard successful")
  }

  suspend fun reInitialize() {
    isLoggedIn = false
    val stacks = _topLevelStacks.value
    val primaryStack = stacks[startKey.first] ?: return

    // Add start screen at beginning
    primaryStack.add(0, startKey.second)
    // Remove everything except start screen
    withFrameNanos { }
    primaryStack.removeAll { it != startKey.second }
    // Remove all other stacks
    withFrameNanos { }
    stacks.keys.removeIf { it != startKey.first }
  }

  private fun requiresLogin(key: T): Boolean = key !is PublicRoute && !isLoggedIn

  private fun isPublic(key: T): Boolean = key is PublicRoute
}

/**
 * Remembers and returns a [TopLevelBackStack] instance for the given start key.
 *
 * @param startKey The initial top-level navigation key.
 * @param loginKey The key used for login screen.
 * @param initialKey The initial key pair after login.
 * @return A remembered [TopLevelBackStack] instance.
 */
@Composable
fun <T : NavKey> rememberTopLevelBackStack(
  startKey: Pair<T, T>,
  loginKey: T,
  initialKey: Pair<T, T> = startKey,
): TopLevelBackStack<NavKey> = remember(startKey) { TopLevelBackStack(startKey, loginKey, initialKey) }
