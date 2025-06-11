package com.greatergoods.meapp.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IEntryService
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashBoardState(
    val isLoading: Boolean = false,
    val last7DaysEntries: List<ScaleEntry> = emptyList(),
    val last30DaysEntries: List<ScaleEntry> = emptyList(),
    val lastYearEntries: List<ScaleEntry> = emptyList(),
    val totalEntries: List<ScaleEntry> = emptyList(),
)

@HiltViewModel
class DashBoardViewmodel @Inject constructor(private val entryService: IEntryService) : ViewModel() {
    private val _state = MutableStateFlow(DashBoardState())
    val state get() = _state.asStateFlow()

    init {
        loadEntries()
    }

    private fun loadEntries() {
        viewModelScope.launch {
            entryService.getEntriesByDeviceType("1", "SCALE").collect { entries ->
                _state.value = _state.value.copy(totalEntries = entries.filterIsInstance<ScaleEntry>())
            }

        }
        viewModelScope.launch {
            entryService.isUpdating.let { isLoading ->
                _state.value = _state.value.copy(isLoading = isLoading.value)
            }
        }
    }

    fun addEntry(entries: List<ScaleEntry>) {
        viewModelScope.launch {
            entryService.addEntry(entries)
        }
    }
}
