package com.greatergoods.meapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Manages multiple top-level navigation stacks for the app's navigation system.
 *
 * @param T The type of navigation key (must implement NavKey).
 * @property topLevelKey The currently selected top-level key.
 * @property backStack The combined navigation back stack for all top-level stacks.
 */
class TopLevelBackStack<T : NavKey>(startKey: NavKey) {

    private var topLevelStacks: LinkedHashMap<NavKey, SnapshotStateList<NavKey>> = linkedMapOf(
        startKey to mutableStateListOf(startKey)
    )

    var topLevelKey by mutableStateOf(startKey)
        private set

    val backStack: NavBackStack = mutableStateListOf(startKey)

    /**
     * Updates the combined back stack from all top-level stacks.
     */
    private fun updateBackStack() {
        backStack.apply {
            clear()
            addAll(topLevelStacks.flatMap { it.value })
        }
    }

    /**
     * Adds or switches to a top-level stack for the given key.
     *
     * @param key The top-level navigation key to add or switch to.
     */
    fun addTopLevel(key: T) {
        if (topLevelStacks[key] == null) {
            topLevelStacks[key] = mutableStateListOf(key)
        } else {
            topLevelStacks.apply {
                remove(key)?.let { put(key, it) }
            }
        }
        topLevelKey = key
        updateBackStack()
    }

    /**
     * Adds a navigation key to the current top-level stack.
     *
     * @param key The navigation key to add.
     */
    fun add(key: T) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    /**
     * Removes the last navigation key from the current top-level stack.
     */
    fun removeLast() {
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        topLevelStacks.remove(removedKey)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
    }

    /**
     * Clears all top-level stacks and resets to the initial key.
     */
    fun clearStack() {
        val initialKey = topLevelStacks.keys.firstOrNull() ?: return
        topLevelStacks.clear()
        topLevelStacks[initialKey] = mutableStateListOf(initialKey)
        topLevelKey = initialKey
        updateBackStack()
    }

    /**
     * Adds multiple navigation keys to the current top-level stack.
     *
     * @param keys The navigation keys to add.
     */
    fun addAll(keys: List<T>) {
        topLevelStacks[topLevelKey]?.addAll(keys)
        updateBackStack()
    }

    /**
     * Replaces the current top-level stack with the given keys.
     *
     * @param keys The navigation keys to set as the new stack.
     */
    fun replaceStack(keys: List<T>) {
        topLevelStacks[topLevelKey]?.clear()
        topLevelStacks[topLevelKey]?.addAll(keys)
        updateBackStack()
    }
}

/**
 * Remembers and returns a [TopLevelBackStack] instance for the given start key.
 *
 * @param startKey The initial top-level navigation key.
 * @return A remembered [TopLevelBackStack] instance.
 */
@Composable
fun <T : NavKey> rememberTopLevelBackStack(startKey: T): TopLevelBackStack<NavKey> =
    remember(startKey) { TopLevelBackStack(startKey) } 