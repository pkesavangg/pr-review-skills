package com.greatergoods.meapp.features.dashboard.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.service.IAppNavigationService
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IDashboardService
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.proto.DashboardKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the dashboard, managing state and handling dashboard intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 * @property appNavigationService The app event service for observing auth state changes.
 */
@HiltViewModel
class DashboardViewModel
@Inject
constructor(
    private val entryService: IEntryService,
    private val appNavigationService: IAppNavigationService,
    private val dashboardService: IDashboardService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
    reducer = DashboardReducer(),
) {
    init {
        handleIntent(DashboardIntent.LoadEntries)
        loadEntries()
        subscribeMetrics()
    }

    override fun provideInitialState(): DashboardState = DashboardState()

    override fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.UpdateVisibleMetrics -> updateVisibleMetrics(intent.metrics)
            else -> null
        }
        super.handleIntent(intent)
    }

    private fun subscribeMetrics() {
        viewModelScope.launch {
            dashboardService.getVisibleKeys().collect {
                handleIntent(DashboardIntent.SetVisibleMetrics(it))
            }
        }
    }

    private fun updateVisibleMetrics(metrics: List<DashboardKey>) {
        viewModelScope.launch {
            dashboardService.updateVisibleKeys(keys = metrics)
        }
    }

    /**
     * Loads entries and updates the state accordingly.
     */
    private fun loadEntries() {
        viewModelScope.launch {
            entryService.getDaywiseBodyScaleLatestWithJoin().collect { dayWise ->
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
