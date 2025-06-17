package com.greatergoods.meapp.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for the history feature, managing state and handling history intents.
 *
 * @property entryService The entry service for fetching history entries.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val entryService: IEntryService
) : BaseIntentViewModel<HistoryState, HistoryIntent>(
    initialState = HistoryState(),
    reducer = HistoryReducer()
) {
    init {
        handleIntent(HistoryIntent.LoadHistory)
        loadHistory()
    }

    /**
     * Loads history entries and updates the state accordingly.
     */
    private fun loadHistory() {
        viewModelScope.launch {
            try {
                entryService.last30Days.collect { entries ->
                    if (entries != null) {
                        handleIntent(HistoryIntent.SetHistoryEntries(entries))
                    }
                }
            } catch (e: Exception) {
                handleIntent(HistoryIntent.SetError(e.message ?: "Failed to load history"))
            }
        }
    }
}
