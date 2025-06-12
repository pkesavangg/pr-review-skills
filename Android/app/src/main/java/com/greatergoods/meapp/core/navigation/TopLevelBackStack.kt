package com.example.nav3integration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

interface PublicRoute

class TopLevelBackStack<T : NavKey>(
    private val startKey: T,
    private val loginKey: T,
    private val initialKey: T // true dashboard/home/etc.
) {

    private var onLoginSuccessRoute: T? = initialKey
    var isLoggedIn by mutableStateOf(false)
        private set

    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(startKey to mutableStateListOf(startKey))

    var topLevelKey by mutableStateOf(startKey)
        private set

    private var _backStack: MutableStateFlow<List<T>> = MutableStateFlow(topLevelStacks.flatMap { it.value })
    val backStack: StateFlow<List<T>> = _backStack.asStateFlow()

    /**
     * Returns the current (top) route in the current top-level stack, or null if empty.
     */
    val currentRoute: T?
        get() = topLevelStacks[topLevelKey]?.lastOrNull()

    private fun updateBackStack() {
        if (topLevelKey == initialKey) {
            _backStack.value = topLevelStacks[initialKey]?.toList() ?: emptyList()
        } else {
            val intalKeyStack = topLevelStacks[initialKey]?.toList() ?: emptyList()
            val currentStack = topLevelStacks[topLevelKey]?.toList() ?: emptyList()
            _backStack.value = intalKeyStack + currentStack
        }
        Log.i("NavHost", "TopLevelBackStack: topLevelKey = $topLevelStacks")
    }

    fun setInitialTopLevel(key: T) {
        topLevelStacks.clear()
        topLevelStacks[key] = mutableStateListOf(key)
        topLevelKey = key
        updateBackStack()
    }

    fun addTopLevel(key: T) {
        if (topLevelStacks[key] == null) {
            topLevelStacks[key] = mutableStateListOf(key)
        } else {
            topLevelStacks.remove(key)?.let { topLevelStacks[key] = it }
        }
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T) {
        if (requiresLogin(key)) {
            onLoginSuccessRoute = key
            topLevelStacks[topLevelKey]?.add(loginKey)
        } else {
            topLevelStacks[topLevelKey]?.add(key)
        }
        updateBackStack()
    }

    fun addAll(keys: List<T>) {
        keys.forEach { add(it) }
    }

    fun removeLast() {

        val currentStack = topLevelStacks[topLevelKey]

        if (topLevelKey == initialKey && (currentStack?.size ?: 0) <= 1) {
            return
        }
        if (currentStack?.size == 1 && currentStack.first() == topLevelKey) {
            topLevelKey = initialKey
        } else {
            currentStack?.removeLastOrNull()
        }
        updateBackStack()
    }

    fun login() {
        isLoggedIn = true
        val target = onLoginSuccessRoute
        onLoginSuccessRoute = null
        val stack = topLevelStacks[topLevelKey]
        val loginIndex = stack?.indexOfLast { it == loginKey } ?: -1
        if (target != null && loginIndex >= 0) {
            stack?.removeAt(loginIndex)
            stack?.add(target)
        }
        updateBackStack()
    }

    fun autoLogin() {
        isLoggedIn = true
        val target = onLoginSuccessRoute ?: initialKey

        setInitialTopLevel(target)
    }

    fun logout() {
        isLoggedIn = false
        topLevelStacks.forEach { (_, stack) ->
            stack.removeAll { !isPublic(it) }
        }
        updateBackStack()
    }

    fun clearStack() {
        topLevelStacks.clear()
        topLevelStacks[initialKey] = mutableStateListOf(initialKey)
        topLevelKey = initialKey
        updateBackStack()
    }

    private fun clearAll() {
        topLevelStacks.clear()
        _backStack.value = emptyList()
        topLevelKey = startKey
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
fun <T : NavKey> rememberTopLevelBackStack(startKey: T, loginKey: T, initialKey: T): TopLevelBackStack<NavKey> =
    remember(startKey) { TopLevelBackStack(startKey, loginKey, initialKey) }
