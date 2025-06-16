package com.greatergoods.meapp.features.entry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.services.IEntryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class EntryViewModel
@Inject
constructor(
    private val entryService: IEntryService,
) : ViewModel() {
    private val _uiState = MutableStateFlow<EntryUiState>(EntryUiState.Loading)
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    private val _selectedMonth = MutableStateFlow<HistoryMonth?>(null)
    val selectedMonth: StateFlow<HistoryMonth?> = _selectedMonth.asStateFlow()

    private val _monthEntries = MutableStateFlow<List<Entry>>(emptyList())
    val monthEntries: StateFlow<List<Entry>> = _monthEntries.asStateFlow()

    private val _selectedEntry = MutableStateFlow<Entry?>(null)
    val selectedEntry: StateFlow<Entry?> = _selectedEntry.asStateFlow()

    init {
        Log.i("CHECKING", "EntryViewModel initialized : ${entryService.latestEntry.value}")
        loadMonths()
    }

    fun loadMonths() {
        viewModelScope.launch {
            _uiState.value = EntryUiState.Success(emptyList()) // Reset state to avoid showing stale data
            try {
                entryService.monthsAll.collect { months ->
                    if (months != null) {
                        _uiState.value = EntryUiState.Success(months)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = EntryUiState.Error(e.message ?: "Failed to load months")
            }
        }
    }

    fun selectMonth(month: HistoryMonth) {
        _selectedMonth.value = month
        loadMonthEntries(month.entryTimestamp ?: "")
    }

    private fun loadMonthEntries(month: String) {
        viewModelScope.launch {
            try {
                entryService.last30Days.collect { entries ->
                    if (entries != null) {
                        _monthEntries.value = entries
                    }
                }
            } catch (e: Exception) {
                _uiState.value = EntryUiState.Error(e.message ?: "Failed to load entries")
            }
        }
    }

    fun selectEntry(entry: Entry) {
        _selectedEntry.value = entry
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch {
            try {
                entryService.deleteEntry(entry)
                // Refresh the current month's entries
                _selectedMonth.value?.let { month ->
                    loadMonthEntries(month.entryTimestamp ?: "")
                }
            } catch (e: Exception) {
                _uiState.value = EntryUiState.Error(e.message ?: "Failed to delete entry")
            }
        }
    }

    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            try {
                Log.i("CHECKING", "EntryViewModel adding entry: $entry")
                entryService.addEntry(entry)
                Log.i("CHECKING", "EntryViewModel entry added successfully")
                // Refresh the current month's entries
                _selectedMonth.value?.let { month ->
                    loadMonthEntries(month.entryTimestamp ?: "")
                }
            } catch (e: Exception) {
                Log.e("CHECKING", "EntryViewModel error adding entry", e)
                _uiState.value = EntryUiState.Error("Failed to add entry: ${e.message}")
            }
        }
    }
}

sealed class EntryUiState {
    data object Loading : EntryUiState()

    data class Success(
        val months: List<HistoryMonth>,
    ) : EntryUiState()

    data class Error(
        val message: String,
    ) : EntryUiState()
}
