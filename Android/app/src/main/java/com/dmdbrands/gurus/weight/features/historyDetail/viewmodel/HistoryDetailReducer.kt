package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
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
  /** Non-null while the full baby edit popover is open for this entry. */
  val babyEditEntry: BabyEntry? = null,
  /** Non-null while the full weight edit bottom sheet is open (manual reading; MOB-1173). */
  val weightEditEntry: ScaleEntry? = null,
  /** Non-null while the BP edit bottom sheet is open (MOB-1173). */
  val bpEditEntry: BpmEntry? = null,
  /** True when this baby day-detail is the baby's birth date — shows the birthday balloon header. */
  val showBirthdayBalloon: Boolean = false,
) : IReducer.State

/**
 * Intent for history detail actions, such as loading and refreshing history.
 */
sealed interface HistoryDetailIntent : IReducer.Intent {
  data object Refresh : HistoryDetailIntent
  data class LoadHistoryDetail(val month: String) : HistoryDetailIntent
  data class SetItemsOpened(val ids: List<Long>) : HistoryDetailIntent
  data class DeleteEntry(val entry: Entry) : HistoryDetailIntent

  /**
   * Weight edit trigger from history (MOB-1173). Opens the weight edit sheet for any source; the
   * sheet enables only the note for device-synced readings (values + metrics read-only).
   */
  data class EditWeightEntry(val entry: ScaleEntry) : HistoryDetailIntent
  data object DismissWeightEditor : HistoryDetailIntent

  /**
   * Saves a weight edit. Manual → edited in place via operationType=edit ([updated] keeps the
   * original row identity, R4 metrics preserved). Device-synced → only the note changes.
   */
  data class SaveWeightEdit(val original: ScaleEntry, val updated: ScaleEntry) : HistoryDetailIntent

  /**
   * BP edit trigger from history (MOB-1173). Opens the BP edit sheet for any source; the sheet
   * enables only the note for device-synced readings (values read-only).
   */
  data class EditBpEntry(val entry: BpmEntry) : HistoryDetailIntent
  data object DismissBpEditor : HistoryDetailIntent

  /**
   * Saves a BP edit (in place via operationType=edit). [updated] keeps the original row identity
   * with the edited systolic/diastolic/pulse/note/timestamp applied (device-synced readings only
   * change the note).
   */
  data class SaveBpEdit(val original: BpmEntry, val updated: BpmEntry) : HistoryDetailIntent

  /** Opens the full baby edit popover (weight/length/notes/date) for [entry]. */
  data class EditBabyEntry(val entry: BabyEntry) : HistoryDetailIntent
  data object DismissBabyEditor : HistoryDetailIntent

  /** Saves edits to a baby [entry] (decigrams/mm already converted from the form). */
  data class SaveBabyEdit(
    val entry: BabyEntry,
    val weightDecigrams: Int?,
    val lengthMillimeters: Int?,
    val note: String?,
    val timestamp: String,
  ) : HistoryDetailIntent
  object Retry : HistoryDetailIntent
  data class SetError(val message: String) : HistoryDetailIntent
  object ClearError : HistoryDetailIntent
  data class SetHistoryItems(
    val month: String,
    val items: List<Entry>,
  ) : HistoryDetailIntent
  data class SetRefreshing(val isRefreshing: Boolean) : HistoryDetailIntent
  data class SetMetric(val isMetric: Boolean) : HistoryDetailIntent

  /** Sets whether this baby day-detail falls on the baby's birth date (birthday balloon header). */
  data class SetBirthdayBalloon(val show: Boolean) : HistoryDetailIntent
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
      // Both manual and device-synced readings open the same edit sheet; the sheet itself enables
      // only the note for device-synced readings (values stay read-only). (MOB-1173)
      is HistoryDetailIntent.EditWeightEntry -> state.copy(weightEditEntry = intent.entry)
      HistoryDetailIntent.DismissWeightEditor -> state.copy(weightEditEntry = null)
      is HistoryDetailIntent.EditBpEntry -> state.copy(bpEditEntry = intent.entry)
      HistoryDetailIntent.DismissBpEditor -> state.copy(bpEditEntry = null)
      is HistoryDetailIntent.EditBabyEntry -> state.copy(babyEditEntry = intent.entry)
      HistoryDetailIntent.DismissBabyEditor -> state.copy(babyEditEntry = null)
      is HistoryDetailIntent.SetHistoryItems ->
        state.copy(
          month = intent.month,
          historyItems = intent.items.toImmutableList(),
          isLoading = false,
          errorMessage = null,
        )
      is HistoryDetailIntent.SetRefreshing -> state.copy(isLoading = intent.isRefreshing)
      is HistoryDetailIntent.SetMetric -> state.copy(isMetric = intent.isMetric)
      is HistoryDetailIntent.SetBirthdayBalloon -> state.copy(showBirthdayBalloon = intent.show)
      HistoryDetailIntent.Retry -> state.copy(isLoading = true)
      else -> state
    }
}
