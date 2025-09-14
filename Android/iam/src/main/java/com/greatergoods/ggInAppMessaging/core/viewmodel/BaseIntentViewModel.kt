package com.greatergoods.ggInAppMessaging.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel for MVI pattern
 * Provides common functionality for state management and intent handling
 */
abstract class BaseIntentViewModel<State, Intent> : ViewModel() {

    private val _state = MutableStateFlow(provideInitialState())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Provides the initial state for the ViewModel
     */
    abstract fun provideInitialState(): State

    /**
     * Handles incoming intents and updates the state accordingly
     * @param intent The intent to handle
     */
    abstract fun handleIntent(intent: Intent)

    /**
     * Updates the current state
     * @param newState The new state to set
     */
    protected fun updateState(newState: State) {
        _state.value = newState
    }

    /**
     * Gets the current state value
     */
    protected val currentState: State
        get() = _state.value

    /**
     * Launches a coroutine in the ViewModel scope
     */
    protected fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
