package com.greatergoods.meapp.features.dashboard.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for the dashboard, managing state and handling dashboard intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val entryService: IEntryService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
    initialState = DashboardState(),
    reducer = DashboardReducer()
) {
    init {
        handleIntent(DashboardIntent.LoadEntries)
        loadEntries()
    }

    /**
     * Loads entries and updates the state accordingly.
     */
    private fun loadEntries() {
        viewModelScope.launch {
            entryService.getDaywiseBodyScaleLatestWithJoin("1").collect { dayWise ->
                handleIntent(DashboardIntent.SetDayWiseEntries(dayWise))
            }
        }
        viewModelScope.launch {
            entryService.getMonthlyBodyScaleAveragesWithJoin("1").collect { monthWise ->
                handleIntent(DashboardIntent.SetMonthWiseEntries(monthWise))
            }
        }
        viewModelScope.launch {
            handleIntent(DashboardIntent.SetIsLoading(entryService.isUpdating.value))
        }
    }

    /**
     * Adds new entries using the entry service and updates the state.
     *
     * @param entries The list of entries to add.
     */
    fun addEntry(entries: List<ScaleEntry>) {
        viewModelScope.launch {
            entryService.addEntry(entries)
            handleIntent(DashboardIntent.AddEntries(entries))
        }
    }
}
