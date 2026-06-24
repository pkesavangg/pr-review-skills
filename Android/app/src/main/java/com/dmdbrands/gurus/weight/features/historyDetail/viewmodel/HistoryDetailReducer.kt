package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * UI state for the history detail feature, holding loading state, error, and data.
 */
@Stable
data class HistoryDetailState(
  val isLoading: Boolean = false,
  val isMetric: Boolean = false,
  val errorMessage: String? = null,
  val month: String = "",
  val itemsOpened: ImmutableList<Long> = persistentListOf(),
  val historyItems: ImmutableList<Entry> = persistentListOf(),
  /** Non-null while the note-edit modal is open for this entry (MOB-438). */
  val noteEditEntry: Entry? = null,
) : IReducer.State

/**
 * Intent for history detail actions, such as loading and refreshing history.
 */
sealed interface HistoryDetailIntent : IReducer.Intent {
  data object Refresh : HistoryDetailIntent
  data class LoadHistoryDetail(val month: String) : HistoryDetailIntent
  data class SetItemsOpened(val ids: List<Long>) : HistoryDetailIntent
  data class DeleteEntry(val entry: Entry) : HistoryDetailIntent
  data class EditEntry(val entry: Entry) : HistoryDetailIntent
  data object DismissNoteEditor : HistoryDetailIntent
  data class SaveNote(val entry: Entry, val note: String) : HistoryDetailIntent
  object Retry : HistoryDetailIntent
  data class SetError(val message: String) : HistoryDetailIntent
  object ClearError : HistoryDetailIntent
  data class SetHistoryItems(
    val month: String,
    val items: List<Entry>,
  ) : HistoryDetailIntent
  data class SetRefreshing(val isRefreshing: Boolean) : HistoryDetailIntent
  data class SetMetric(val isMetric: Boolean) : HistoryDetailIntent
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
      is HistoryDetailIntent.SetItemsOpened -> state.copy(itemsOpened = intent.ids.toImmutableList())
      is HistoryDetailIntent.EditEntry -> state.copy(noteEditEntry = intent.entry)
      HistoryDetailIntent.DismissNoteEditor -> state.copy(noteEditEntry = null)
      is HistoryDetailIntent.SetHistoryItems ->
        state.copy(
          month = intent.month,
          historyItems = intent.items.toImmutableList(),
          isLoading = false,
          errorMessage = null,
        )
      is HistoryDetailIntent.SetRefreshing -> state.copy(isLoading = intent.isRefreshing)
      is HistoryDetailIntent.SetMetric -> state.copy(isMetric = intent.isMetric)
      HistoryDetailIntent.Retry -> state.copy(isLoading = true)
      else -> state
    }
}
