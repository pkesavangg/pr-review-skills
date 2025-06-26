package com.greatergoods.meapp.features.dashboard.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

/**
 * ViewModel for the dashboard, managing state and handling dashboard intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 */
@HiltViewModel
class
DashboardViewModel @Inject constructor(
    private val entryService: IEntryService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
    reducer = DashboardReducer(),
) {
    init {
        handleIntent(DashboardIntent.LoadEntries)
        loadEntries()
    }

    override fun provideInitialState(): DashboardState {
        return DashboardState()
    }

    /**
     * Loads entries and updates the state accordingly.
     */
    private fun loadEntries() {
        viewModelScope.launch {
            entryService.getDaywiseBodyScaleLatestWithJoin().collect { dayWise ->
                Log.i("CHECKING", dayWise.toString())
                handleIntent(DashboardIntent.SetDayWiseEntries(dayWise))
            }
        }
        viewModelScope.launch {
            entryService.getMonthlyBodyScaleAveragesWithJoin().collect { monthWise ->
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
            dialogQueueService.showToast(
                Toast(
                    message = "Adding ${entries.size} entries",
                ),
            )
        }
    }
}
