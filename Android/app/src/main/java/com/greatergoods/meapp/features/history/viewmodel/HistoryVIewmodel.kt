package com.greatergoods.meapp.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.history.components.HistoryItemModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val entryService: IEntryService,
    ) : BaseIntentViewModel<HistoryState, HistoryIntent>(
            HistoryReducer(),
        ) {
        override fun provideInitialState(): HistoryState = HistoryState()

        init {
            loadHistory()
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
                    // if (entries != null) {
                    //     handleIntent(HistoryIntent.SetHistoryItems(entries))
                    // }
                }
                // TODO: Load history from repository/service
                // For now, just set a sample list
                val sampleItems =
                    listOf(
                        HistoryItemModel("Dec 2022", "5 Entries", "148.6 lbs", "-1.4 lbs"),
                        HistoryItemModel("Nov 2022", "6 Entries", "150.0 lbs", "+0.2 lbs"),
                        HistoryItemModel("Oct 2022", "4 Entries", "140.0 lbs", "+0.2 lbs"),
                    )
                handleIntent(HistoryIntent.SetHistoryItems(sampleItems))
            }
        }

        override fun handleIntent(intent: HistoryIntent) {
            super.handleIntent(intent)
        }
    }
