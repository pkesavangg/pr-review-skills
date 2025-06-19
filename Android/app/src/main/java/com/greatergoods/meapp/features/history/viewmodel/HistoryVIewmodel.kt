package com.greatergoods.meapp.features.history.viewmodel

import android.util.Log
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

    init {
        handleIntent(HistoryIntent.LoadHistory)
        viewModelScope.launch {

            entryService.getMonthlyAverage().collect {
                Log.i("CHECKING", "Monthly Average: $it")
            }
        }
    }
}
