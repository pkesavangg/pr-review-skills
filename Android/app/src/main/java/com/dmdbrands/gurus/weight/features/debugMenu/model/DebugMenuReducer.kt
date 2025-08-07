package com.dmdbrands.gurus.weight.features.debugMenu.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer

/**
 * Reducer for Debug Menu screen state transitions.
 * Handles all state changes based on intents.
 */
class DebugMenuReducer : IReducer<DebugMenuState, DebugMenuIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: DebugMenuState,
        intent: DebugMenuIntent,
    ): DebugMenuState =
        when (intent) {
            is DebugMenuIntent.OnBack -> {
                state.copy(isLoading = false)
            }

            is DebugMenuIntent.SendLogs -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.ResyncEntries -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.ClearAllData -> {
                state.copy(isLoading = true)
            }

            is DebugMenuIntent.SendScaleLogs -> {
                state.copy(isLoading = true)
            }
        }
}
