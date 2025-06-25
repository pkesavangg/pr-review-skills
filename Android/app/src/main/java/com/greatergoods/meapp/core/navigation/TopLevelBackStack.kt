package com.example.nav3integration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PublicRoute

class TopLevelBackStack<T : NavKey>(
    private val startKey: Pair<T, T>,
    private val loginKey: T,
    private val initialKey: Pair<T, T> = startKey,
) {

    private var onLoginSuccessRoute: Pair<T, T>? = null
    var isLoggedIn by mutableStateOf(false)
        private set

    private val _topLevelStacks = MutableStateFlow(
        linkedMapOf<T, SnapshotStateList<T>>(
            startKey.first to mutableStateListOf(startKey.second),
        ),
    )
    val topLevelStacks: StateFlow<LinkedHashMap<T, SnapshotStateList<T>>> = _topLevelStacks.asStateFlow()

    fun addRoute(route: T, topLevel: T? = null, popUpTo: T? = null) {
        val topLevelKey = topLevel ?: startKey.first
        val stacks = _topLevelStacks.value
        val stack = stacks.getOrPut(topLevelKey) { mutableStateListOf() }

        if (requiresLogin(route)) {
            onLoginSuccessRoute = Pair(topLevelKey, route)
            stack.add(loginKey)
        } else {
            popUpTo?.let { popTarget ->
                stack.clear()
                stack.add(popTarget)
            }
            if (popUpTo != route) {
                stack.add(route)
            }
        }
    }

    fun replaceStack(routes: List<T>, topLevel: T? = null) {
        val topLevelKey = topLevel ?: startKey.first
        val stacks = _topLevelStacks.value
        val stack = stacks.getOrPut(topLevelKey) { mutableStateListOf() }

        stack.clear()
        routes.forEach { route ->
            if (requiresLogin(route)) {
                onLoginSuccessRoute = Pair(topLevelKey, route)
                stack.add(loginKey)
            } else {
                stack.add(route)
            }
        }
    }

    fun getStackForTopLevel(topLevel: T): SnapshotStateList<T> {
        return _topLevelStacks.value[topLevel] ?: mutableStateListOf()
    }

    fun removeLast(topLevel: T? = null) {
        val topLevelKey = topLevel ?: startKey.first
        val stacks = _topLevelStacks.value
        val stack = stacks[topLevelKey] ?: return

        if (stack.isNotEmpty()) {
            stack.removeLastOrNull()
            if (stack.isEmpty()) {
                stacks.remove(topLevelKey)
            }
        }
    }

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

    fun autoLogin() {
        isLoggedIn = true
        val stacks = _topLevelStacks.value
        val stack = stacks.getOrPut(startKey.first) { mutableStateListOf() }

        stack.clear()
        stack.add(initialKey.first)

        addRoute(initialKey.second, initialKey.first)
        AppLog.i("autoLogin", "Navigation to dashboard successful")
    }

    fun logout() {
        isLoggedIn = false
        val stacks = _topLevelStacks.value
        stacks.values.forEach { stack ->
            // Remove non-public routes from each stack
            val publicRoutes = stack.filter { isPublic(it) }
            stack.clear()
            stack.addAll(publicRoutes)
        }
    }

    private fun requiresLogin(key: T): Boolean =
        key !is PublicRoute && !isLoggedIn

    private fun isPublic(key: T): Boolean =
        key is PublicRoute
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
): TopLevelBackStack<NavKey> =
    remember(startKey) { TopLevelBackStack(startKey, loginKey, initialKey) }
