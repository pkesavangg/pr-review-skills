package com.greatergoods.meapp.features.history.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer

/**
 * UI state for the history feature, holding loading state, error, and data.
 */
data class HistoryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val historyItems: List<Any> = emptyList(), // Replace Any with your model
) : IReducer.State

/**
 * Intent for history actions, such as loading and refreshing history.
 */
sealed interface HistoryIntent : IReducer.Intent {
    object LoadHistory : HistoryIntent

    object Retry : HistoryIntent

    data class SetError(
        val message: String,
    ) : HistoryIntent

    object ClearError : HistoryIntent

    data class SetHistoryItems(
        val items: List<Any>,
    ) : HistoryIntent
}

/**
 * Reducer for the history state, handling intents to update state and errors.
 */
class HistoryReducer : IReducer<HistoryState, HistoryIntent> {
    override fun reduce(
        state: HistoryState,
        intent: HistoryIntent,
    ): HistoryState? =
        when (intent) {
            is HistoryIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
            HistoryIntent.ClearError -> state.copy(errorMessage = null)
            HistoryIntent.LoadHistory -> state.copy(isLoading = true)
            is HistoryIntent.SetHistoryItems ->
                state.copy(
                    historyItems = intent.items,
                    isLoading = false,
                    errorMessage = null,
                )

            HistoryIntent.Retry -> state.copy(isLoading = true)
            else -> null
        }
}
