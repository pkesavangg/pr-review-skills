package com.greatergoods.meapp.features.historyDetail.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.historyDetail.components.HistoryDetailItemModel

/**
 * UI state for the history detail feature, holding loading state, error, and data.
 */
data class HistoryDetailState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val month: String = "",
    val historyItems: List<HistoryDetailItemModel> = emptyList(),
) : IReducer.State

/**
 * Intent for history detail actions, such as loading and refreshing history.
 */
sealed interface HistoryDetailIntent : IReducer.Intent {
    data class LoadHistoryDetail(val month: String) : HistoryDetailIntent
    object Retry : HistoryDetailIntent
    data class SetError(val message: String) : HistoryDetailIntent
    object ClearError : HistoryDetailIntent
    data class SetHistoryItems(
        val month: String,
        val items: List<HistoryDetailItemModel>,
    ) : HistoryDetailIntent
}

/**
 * Reducer for the history detail state, handling intents to update state and errors.
 */
class HistoryDetailReducer : IReducer<HistoryDetailState, HistoryDetailIntent> {
    override fun reduce(
        state: HistoryDetailState,
        intent: HistoryDetailIntent,
    ): HistoryDetailState? =
        when (intent) {
            is HistoryDetailIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
            HistoryDetailIntent.ClearError -> state.copy(errorMessage = null)
            is HistoryDetailIntent.LoadHistoryDetail -> state.copy(isLoading = true)
            is HistoryDetailIntent.SetHistoryItems ->
                state.copy(
                    month = intent.month,
                    historyItems = intent.items,
                    isLoading = false,
                    errorMessage = null,
                )
            HistoryDetailIntent.Retry -> state.copy(isLoading = true)
        }
}
