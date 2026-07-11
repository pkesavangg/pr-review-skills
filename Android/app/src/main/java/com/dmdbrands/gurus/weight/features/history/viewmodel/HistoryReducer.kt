package com.dmdbrands.gurus.weight.features.history.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * UI state for the history feature, holding loading state, error, and data.
 */
@Stable
data class HistoryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val historyItems: ImmutableList<HistoryMonth> = persistentListOf(),
    val bpHistoryItems: ImmutableList<BpHistoryMonth> = persistentListOf(),
    // Keyed by babyId so each baby's history stays scoped to that baby. A single shared
    // list caused every baby to display the last-loaded baby's entries (MOB-1449).
    val babyHistoryItems: ImmutableMap<String, ImmutableList<BabyWeekGroup>> = persistentMapOf(),
) : IReducer.State

/**
 * Intent for history actions, such as loading and refreshing history.
 */
sealed interface HistoryIntent : IReducer.Intent {
    data class Loading(val isLoading: Boolean) : HistoryIntent

    object Retry : HistoryIntent

    data class SetError(
        val message: String,
    ) : HistoryIntent

    object ClearError : HistoryIntent

    data class SetHistoryItems(
        val items: List<HistoryMonth>,
    ) : HistoryIntent

    object Refresh : HistoryIntent

    data class SetBpHistoryItems(val items: List<BpHistoryMonth>) : HistoryIntent

    data class SetBabyHistoryItems(val babyId: String, val items: List<BabyWeekGroup>) : HistoryIntent

    object Export : HistoryIntent
    object OnConnectScale : HistoryIntent
}

/**
 * Reducer for the history state, handling intents to update state and errors.
 */
class HistoryReducer : IReducer<HistoryState, HistoryIntent> {
    override fun reduce(
        state: HistoryState,
        intent: HistoryIntent,
    ): HistoryState =
        when (intent) {
            is HistoryIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
            HistoryIntent.ClearError -> state.copy(errorMessage = null)
            is HistoryIntent.Loading -> state.copy(isLoading = intent.isLoading)
            is HistoryIntent.SetHistoryItems ->
                state.copy(
                    historyItems = intent.items.toImmutableList(),
                    isLoading = false,
                    errorMessage = null,
                )

            is HistoryIntent.SetBpHistoryItems ->
                state.copy(
                    bpHistoryItems = intent.items.toImmutableList(),
                    isLoading = false,
                    errorMessage = null,
                )

            is HistoryIntent.SetBabyHistoryItems ->
                state.copy(
                    babyHistoryItems =
                        (state.babyHistoryItems + (intent.babyId to intent.items.toImmutableList()))
                            .toImmutableMap(),
                    isLoading = false,
                    errorMessage = null,
                )

            HistoryIntent.Retry -> state.copy(isLoading = true)
            else -> state
        }
}
