package com.greatergoods.meapp.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

/**
 * ViewModel for the history feature, managing state and handling history intents.
 *
 * @property entryService The entry service for fetching history entries.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val entryService: IEntryService
) : BaseIntentViewModel<HistoryState, HistoryIntent>(
    reducer = HistoryReducer(),
) {

    override fun provideInitialState(): HistoryState {
        return HistoryState()
    }

    init {
        handleIntent(HistoryIntent.LoadHistory)
        viewModelScope.launch {
            Log.i("CHECKING", "Monthly Average:")

            entryService.monthlyAverage.collect {
                Log.i("CHECKING", "Monthly Average: $it")
            }
        }
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
