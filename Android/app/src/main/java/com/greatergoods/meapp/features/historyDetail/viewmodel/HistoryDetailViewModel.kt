package com.greatergoods.meapp.features.historyDetail.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(
    assistedFactory = HistoryDetailViewModel.Factory::class
)
class HistoryDetailViewModel @AssistedInject constructor(
    private val entryService: IEntryService,
    @Assisted val month: String,
) : BaseIntentViewModel<HistoryDetailState, HistoryDetailIntent>(HistoryDetailReducer()) {

    @AssistedFactory
    interface Factory {
        fun create(month: String): HistoryDetailViewModel
    }

    override fun provideInitialState(): HistoryDetailState = HistoryDetailState()

    init {
        loadHistoryDetail()
    }

    private fun loadHistoryDetail() {
        viewModelScope.launch {
            entryService.monthDetails(month).collect {
                handleIntent(HistoryDetailIntent.SetHistoryItems(month, it.filterIsInstance<ScaleEntry>()))
            }
        }
    }

    override fun handleIntent(intent: HistoryDetailIntent) {
        super.handleIntent(intent)
        when (intent) {
            is HistoryDetailIntent.Refresh -> {
                viewModelScope.launch {
                    entryService.syncOperations()
                }
            }

            else -> Unit
        }
    }
}
