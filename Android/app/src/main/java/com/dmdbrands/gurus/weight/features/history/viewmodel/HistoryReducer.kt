package com.dmdbrands.gurus.weight.features.history.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth

/**
 * UI state for the history feature, holding loading state, error, and data.
 */
data class HistoryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val historyItems: List<HistoryMonth> = emptyList(), // Replace Any with your model
) : IReducer.State

/**
 * Intent for history actions, such as loading and refreshing history.
 */
sealed interface HistoryIntent : IReducer.Intent {
    data class Loading(val isLoading: Boolean) : HistoryIntent

    object Retry : HistoryIntent

    data class SetError(
        val message: String,
    ) : HistoryIntent

    object ClearError : HistoryIntent

    data class SetHistoryItems(
        val items: List<HistoryMonth>,
    ) : HistoryIntent

    object Refresh : HistoryIntent

    data class getHistory(
        val start: String,
    ) : HistoryIntent
}

/**
 * Reducer for the history state, handling intents to update state and errors.
 */
class HistoryReducer : IReducer<HistoryState, HistoryIntent> {
    override fun reduce(
        state: HistoryState,
        intent: HistoryIntent,
    ): HistoryState =
        when (intent) {
            is HistoryIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
            HistoryIntent.ClearError -> state.copy(errorMessage = null)
            is HistoryIntent.Loading -> state.copy(isLoading = intent.isLoading)
            is HistoryIntent.SetHistoryItems ->
                state.copy(
                    historyItems = intent.items,
                    isLoading = false,
                    errorMessage = null,
                )

            HistoryIntent.Retry -> state.copy(isLoading = true)
            else -> state
        }
}
