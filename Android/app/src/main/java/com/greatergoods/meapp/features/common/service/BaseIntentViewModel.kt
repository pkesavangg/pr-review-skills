package com.greatergoods.meapp.features.common.service

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.common.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseIntentViewModel<State : IReducer.State, Intent : IReducer.Intent>(
    private val reducer: IReducer<State, Intent>
) : BaseViewModel() {

    protected val _state: MutableStateFlow<State> = MutableStateFlow(provideInitialState())
    val state: StateFlow<State>
        get() = _state.asStateFlow()

    protected val _event: MutableSharedFlow<Intent> = MutableSharedFlow()

    // Subclass must provide initial state
    protected abstract fun provideInitialState(): State
    val event: SharedFlow<Intent>
        get() = _event.asSharedFlow()

    private fun sendIntent(event: Intent) {
        val newState = reducer.reduce(_state.value, event)
        if (newState != null) {
            _state.value = newState
        }
    }

    open fun handleIntent(intent: Intent) {
        this.sendIntent(intent)
    }
}
