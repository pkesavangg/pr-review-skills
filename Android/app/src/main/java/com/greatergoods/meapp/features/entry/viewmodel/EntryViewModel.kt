package com.greatergoods.meapp.features.entry.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for the entry feature, managing state and handling entry intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 */
@HiltViewModel
class EntryViewModel @Inject constructor(
    private val entryService: IEntryService
) : BaseIntentViewModel<EntryState, EntryIntent>(
    initialState = EntryState(),
    reducer = EntryReducer()
) {
    init {
        handleIntent(EntryIntent.LoadMonths)
        loadMonths()
    }

    /**
     * Loads months and updates the state accordingly.
     */
    private fun loadMonths() {
        viewModelScope.launch {
            try {
                entryService.monthsAll.collect { months ->
                    if (months != null) {
                        handleIntent(EntryIntent.SetMonths(months))
                    }
                }
            } catch (e: Exception) {
                handleIntent(EntryIntent.SetError(e.message ?: "Failed to load months"))
            }
        }
    }

    /**
     * Selects a month and loads its entries.
     *
     * @param month The month to select.
     */
    fun selectMonth(month: HistoryMonth) {
        handleIntent(EntryIntent.SelectMonth(month))
        loadMonthEntries(month.entryTimestamp ?: "")
    }

    private fun loadMonthEntries(month: String) {
        viewModelScope.launch {
            try {
                entryService.last30Days.collect { entries ->
                    if (entries != null) {
                        handleIntent(EntryIntent.SetMonthEntries(entries))
                    }
                }
            } catch (e: Exception) {
                handleIntent(EntryIntent.SetError(e.message ?: "Failed to load entries"))
            }
        }
    }

    /**
     * Selects an entry.
     *
     * @param entry The entry to select.
     */
    fun selectEntry(entry: Entry) {
        handleIntent(EntryIntent.SelectEntry(entry))
    }

    /**
     * Deletes an entry and refreshes the current month's entries.
     *
     * @param entry The entry to delete.
     */
    fun deleteEntry(entry: Entry) {
        viewModelScope.launch {
            try {
                entryService.deleteEntry(entry)
                state.value.selectedMonth?.let { month ->
                    loadMonthEntries(month.entryTimestamp ?: "")
                }
            } catch (e: Exception) {
                handleIntent(EntryIntent.SetError(e.message ?: "Failed to delete entry"))
            }
        }
    }

    /**
     * Adds an entry and refreshes the current month's entries.
     *
     * @param entry The entry to add.
     */
    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            try {
                entryService.addEntry(entry)
                state.value.selectedMonth?.let { month ->
                    loadMonthEntries(month.entryTimestamp ?: "")
                }
            } catch (e: Exception) {
                handleIntent(EntryIntent.SetError("Failed to add entry: ${e.message}"))
            }
        }
    }
}
