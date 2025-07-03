package com.greatergoods.meapp.features.help.model

import com.greatergoods.meapp.domain.interfaces.IReducer

/**
 * Reducer for Help screen state transitions.
 */
class HelpReducer : IReducer<HelpState, HelpIntent> {
    /**
     * Reduces the current state and intent to a new state.
     * @param state The current state.
     * @param intent The intent/action to handle.
     * @return The new state after applying the intent.
     */
    override fun reduce(
        state: HelpState,
        intent: HelpIntent,
    ): HelpState =
        when (intent) {
            HelpIntent.ShowModelNumberHelpPopup -> {
                state.copy(isLoading = false, error = null)
            }
            is HelpIntent.OnBack -> {
                state.copy(isLoading = false, error = null)
            }

            is HelpIntent.OpenDebugMenu -> {
                state.copy(isLoading = false, error = null)
            }

            is HelpIntent.OpenUrl -> {
                state.copy(isLoading = false, error = null)
            }

            is HelpIntent.Error -> {
                state.copy(isLoading = false, error = intent.message)
            }

            is HelpIntent.OpenDebugMenu -> {
                state.copy(isLoading = false, error = null)
            }
        }
}
