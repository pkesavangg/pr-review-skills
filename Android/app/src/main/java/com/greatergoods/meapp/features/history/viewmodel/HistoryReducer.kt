package com.greatergoods.meapp.features.history.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.entry.Entry

/**
 * UI state for the history feature, holding loading state, entries, and errors.
 *
 * @property isLoading Whether data is currently loading.
 * @property historyEntries List of history entries.
 * @property errorMessage Error message if any error occurs.
 */
data class HistoryState(
    val isLoading: Boolean = false,
    val historyEntries: List<Entry> = emptyList(),
    val errorMessage: String? = null,
) : IReducer.State

/**
 * Intent for history actions, such as loading and setting entries.
 */
sealed interface HistoryIntent : IReducer.Intent {
    object LoadHistory : HistoryIntent
    data class SetHistoryEntries(val entries: List<Entry>) : HistoryIntent
    data class SetError(val message: String) : HistoryIntent
    object ClearError : HistoryIntent
}

/**
 * Reducer for the history state, handling intents to update entries and errors.
 */
class HistoryReducer : IReducer<HistoryState, HistoryIntent> {
    override fun reduce(state: HistoryState, intent: HistoryIntent): HistoryState? = when (intent) {
        is HistoryIntent.SetHistoryEntries -> state.copy(historyEntries = intent.entries, isLoading = false, errorMessage = null)
        is HistoryIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
        HistoryIntent.ClearError -> state.copy(errorMessage = null)
        HistoryIntent.LoadHistory -> state.copy(isLoading = true)
    }
}
