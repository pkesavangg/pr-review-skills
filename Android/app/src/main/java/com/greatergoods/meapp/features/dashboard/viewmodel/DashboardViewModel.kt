package com.greatergoods.meapp.features.dashboard.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.service.IAppEventService
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.AuthState
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.strings.ToastStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the dashboard, managing state and handling dashboard intents.
 *
 * @property entryService The entry service for fetching and updating entries.
 * @property appEventService The app event service for observing auth state changes.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val entryService: IEntryService,
    private val appEventService: IAppEventService
) : BaseIntentViewModel<DashboardState, DashboardIntent>(
    reducer = DashboardReducer(),
) {
    init {
        handleIntent(DashboardIntent.LoadEntries)
        loadEntries()
        observeAuthEvent()
    }

    override fun provideInitialState(): DashboardState {
        return DashboardState()
    }

    /**
     * Observes authentication state changes and shows toast for account switches.
     */
    private fun observeAuthEvent() {
        viewModelScope.launch {
            appEventService.authEvent.collect { authState ->
                when (authState) {
                    is AuthState.AccountSwitched -> {
                        val accountName = authState.account.firstName ?: authState.account.email
                        dialogQueueService.showToast(
                            Toast(
                                title = null,
                                message = ToastStrings.Success.AccountSwitchSuccess.Message(accountName),
                                action = null,
                            ),
                        )
                    }
                    else -> {
                        // Handle other auth states if needed
                    }
                }
            }
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
