package com.greatergoods.meapp.features.historyDetail.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.domain.services.IEntryService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.historyDetail.components.HistoryDetailItemModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    private val entryService: IEntryService,
    savedStateHandle: SavedStateHandle,
) : BaseIntentViewModel<HistoryDetailState, HistoryDetailIntent>(HistoryDetailReducer()) {
    private val month: String = checkNotNull(savedStateHandle["month"]) { "month parameter is required" }

    override fun provideInitialState(): HistoryDetailState = HistoryDetailState()

    init {
        loadHistoryDetail()
    }

    fun loadHistoryDetail() {
        viewModelScope.launch {
            handleIntent(HistoryDetailIntent.LoadHistoryDetail(month))

            // TODO: Load history from repository/service
            // For now, just set a sample list
            val sampleItems = listOf(
                HistoryDetailItemModel(
                    date = "Dec 16",
                    time = "2:10 PM",
                    weight = "149.2",
                ),
                HistoryDetailItemModel(
                    date = "Dec 10",
                    time = "2:10 PM",
                    weight = "148.7",
                ),
            )
            handleIntent(HistoryDetailIntent.SetHistoryItems(month, sampleItems))
        }
    }

    override fun handleIntent(intent: HistoryDetailIntent) {
        super.handleIntent(intent)
    }
}
