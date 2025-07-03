package com.greatergoods.meapp.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    private val entryService: IEntryService,
) : BaseIntentViewModel<HistoryState, HistoryIntent>(
    HistoryReducer(),
) {
    override fun provideInitialState(): HistoryState = HistoryState()

    override fun handleIntent(intent: HistoryIntent) {
        super.handleIntent(intent)
        when (intent) {
            is HistoryIntent.Refresh -> {
                resync()
            }

            is HistoryIntent.getHistory -> {
                viewModelScope.launch {
                    entryService.monthDetails(intent.start).collect {
                    }
                }
            }

            else -> null
        }
    }

    init {
        loadHistory()
        viewModelScope.launch {
            entryService.isUpdating.collect {
                handleIntent(HistoryIntent.Loading(it))
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            handleIntent(HistoryIntent.Loading(true))
            entryService.getMonthlyAverage().collect {
                handleIntent(
                    HistoryIntent.SetHistoryItems(
                        items = it,
                    ),
                )
            }
        }
    }

    private fun resync() {
        viewModelScope.launch {
            entryService.syncOperations()
        }
    }
}
