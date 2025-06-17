package com.greatergoods.meapp.features.entry.viewmodel

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.Entry

/**
 * UI state for the entry feature, holding loading state, months, entries, and errors.
 *
 * @property isLoading Whether data is currently loading.
 * @property months List of history months.
 * @property selectedMonth The currently selected month.
 * @property monthEntries List of entries for the selected month.
 * @property selectedEntry The currently selected entry.
 * @property errorMessage Error message if any error occurs.
 */
data class EntryState(
    val isLoading: Boolean = false,
    val months: List<HistoryMonth> = emptyList(),
    val selectedMonth: HistoryMonth? = null,
    val monthEntries: List<Entry> = emptyList(),
    val selectedEntry: Entry? = null,
    val errorMessage: String? = null,
) : IReducer.State

/**
 * Intent for entry actions, such as loading, selecting, adding, and deleting entries.
 */
sealed interface EntryIntent : IReducer.Intent {
    object LoadMonths : EntryIntent
    data class SetMonths(val months: List<HistoryMonth>) : EntryIntent
    data class SelectMonth(val month: HistoryMonth) : EntryIntent
    data class SetMonthEntries(val entries: List<Entry>) : EntryIntent
    data class SelectEntry(val entry: Entry) : EntryIntent
    data class AddEntry(val entry: Entry) : EntryIntent
    data class DeleteEntry(val entry: Entry) : EntryIntent
    data class SetError(val message: String) : EntryIntent
    object ClearError : EntryIntent
}

/**
 * Reducer for the entry state, handling intents to update months, entries, and errors.
 */
class EntryReducer : IReducer<EntryState, EntryIntent> {
    override fun reduce(state: EntryState, intent: EntryIntent): EntryState? = when (intent) {
        is EntryIntent.SetMonths -> state.copy(months = intent.months, isLoading = false, errorMessage = null)
        is EntryIntent.SelectMonth -> state.copy(selectedMonth = intent.month, isLoading = false, errorMessage = null)
        is EntryIntent.SetMonthEntries -> state.copy(monthEntries = intent.entries, isLoading = false, errorMessage = null)
        is EntryIntent.SelectEntry -> state.copy(selectedEntry = intent.entry, errorMessage = null)
        is EntryIntent.AddEntry -> state.copy(monthEntries = state.monthEntries + intent.entry, errorMessage = null)
        is EntryIntent.DeleteEntry -> state.copy(monthEntries = state.monthEntries.filter { it != intent.entry }, errorMessage = null)
        is EntryIntent.SetError -> state.copy(errorMessage = intent.message, isLoading = false)
        EntryIntent.ClearError -> state.copy(errorMessage = null)
        EntryIntent.LoadMonths -> state.copy(isLoading = true)
    }
}
