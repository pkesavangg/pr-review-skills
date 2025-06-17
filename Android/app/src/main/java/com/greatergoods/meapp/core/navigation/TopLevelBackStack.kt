package com.example.nav3integration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

interface PublicRoute

class TopLevelBackStack<T : NavKey>(
    private val startKey: Pair<T, T>,
    private val loginKey: T,
    private val initialKey: Pair<T, T> = startKey,
) {

    private var onLoginSuccessRoute: Pair<T, T>? = null
    var isLoggedIn by mutableStateOf(false)
        private set

    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(startKey.first to mutableStateListOf(startKey.second))

    fun addRoute(route: T, topLevel: T? = null, popUpTo: T? = null) {
        // Ensure the stack exists or create it
        val topLevel = topLevel ?: startKey.first
        val stack = topLevelStacks.getOrPut(topLevel) { mutableStateListOf() }
        if (requiresLogin(route)) {
            onLoginSuccessRoute = Pair(topLevel, route)
            stack.add(loginKey)
        } else {
            stack.apply {
                popUpTo?.let {
                    clear()
                    add(it)
                }
                if (popUpTo != route)
                    add(route)
            }
        }
    }

    fun replaceStack(route: List<T>, topLevel: T? = null) {
        val topLevel = topLevel ?: startKey.first
        val stack = topLevelStacks.getOrPut(topLevel) { mutableStateListOf() }
        stack.apply {
            clear()
            route.forEach { r ->
                if (requiresLogin(r)) {
                    onLoginSuccessRoute = Pair(topLevel, r)
                    stack.add(loginKey)
                } else {
                    stack.add(r)
                }
            }
        }
    }

    fun getStackForTopLevel(topLevel: T): List<T> {
        return topLevelStacks[topLevel]?.toList() ?: emptyList()
    }

    fun removeLast(topLevel: T? = null) {
        val topLevel = topLevel ?: initialKey.first
        val stack = topLevelStacks[topLevel]
        if (stack != null && stack.isNotEmpty()) {
            stack.removeLastOrNull()
            if (stack.isEmpty()) {
                topLevelStacks.remove(topLevel)
            }
        }
    }

    fun login() {
        isLoggedIn = true
        val target = onLoginSuccessRoute
        onLoginSuccessRoute = null

        target?.let { (stackKey, newRoute) ->
            val stack = topLevelStacks[stackKey]
            val loginIndex = stack?.indexOfLast { it == loginKey } ?: -1

            if (loginIndex >= 0 && stack != null) {
                stack.removeAt(loginIndex)
                stack.add(newRoute)
            }
        }
    }

    fun autoLogin() {
        isLoggedIn = true
        topLevelStacks[startKey.first]?.apply {
            clear()
            add(initialKey.first)
        }
        addRoute(initialKey.second, initialKey.first)
    }

    fun logout() {
        isLoggedIn = false
        topLevelStacks.forEach { (_, stack) ->
            stack.removeAll { !isPublic(it) }
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
 * @return A remembered [TopLevelBackStack] instance.
 */
@Composable
fun <T : NavKey> rememberTopLevelBackStack(
    startKey: Pair<T, T>,
    loginKey: T,
    initialKey: Pair<T, T> = startKey,
): TopLevelBackStack<NavKey> =
    remember(startKey) { TopLevelBackStack(startKey, loginKey, initialKey) }
