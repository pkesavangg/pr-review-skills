package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel(
    assistedFactory = HistoryDetailViewModel.Factory::class,
)
class HistoryDetailViewModel @AssistedInject constructor(
    private val accountService: IAccountService,
    private val entryService: IEntryService,
    private val healthConnectService: IHealthConnectService,
    private val entryReadService: IEntryReadService,
    @Assisted val month: String,
    @Assisted val productType: ProductType,
) : BaseIntentViewModel<HistoryDetailState, HistoryDetailIntent>(HistoryDetailReducer()) {

    @AssistedFactory
    interface Factory {
        fun create(month: String, productType: ProductType): HistoryDetailViewModel
    }

    override fun provideInitialState(): HistoryDetailState = HistoryDetailState()

    override fun onDependenciesReady() {
        AppLog.d(TAG, "HistoryDetailViewModel ready for month: $month")
        viewModelScope.launch {
            accountService.activeAccount
                .map { it?.isMetricUnit() ?: false }
                .distinctUntilChanged()
                .collect { handleIntent(HistoryDetailIntent.SetMetric(it)) }
        }
        loadDetail()
    }

    private fun loadDetail() {
        val product = productSelectionManager.selectedProduct.value
        AppLog.d(TAG, "Loading ${product.productType} details for key: $month")
        viewModelScope.launch {
            try {
                entryReadService.getDetail(product, month).collect { detail ->
                    val entries: List<Entry> = when (detail) {
                        is HistoryDetail.Weight -> detail.entries
                        is HistoryDetail.BloodPressure -> detail.entries
                        is HistoryDetail.Baby -> detail.entries
                    }
                    if (entries.isNotEmpty()) {
                        AppLog.d(TAG, "Loaded ${entries.size} entries")
                        handleIntent(HistoryDetailIntent.SetHistoryItems(month, entries))
                    } else {
                        AppLog.w(TAG, "No entries found for key: $month")
                        handleIntent(HistoryDetailIntent.SetError("No entries found"))
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error loading details for key: $month", e)
                handleIntent(HistoryDetailIntent.SetError(e.message ?: "Unknown error"))
            }
        }
    }

    override fun handleIntent(intent: HistoryDetailIntent) {
        super.handleIntent(intent)
        when (intent) {

            is HistoryDetailIntent.Refresh -> {
                AppLog.d(TAG, "Refreshing history details")
                viewModelScope.launch {
                    try {
                        // Set loading state to true
                        handleIntent(HistoryDetailIntent.SetRefreshing(true))
                        // Perform sync operations
                        entryService.syncOperations()
                        AppLog.d(TAG, "Sync operations completed")

                    } catch (e: Exception) {
                        AppLog.e(TAG, "Error during sync operations", e)
                        handleIntent(HistoryDetailIntent.SetError("Failed to refresh data"))
                    } finally {
                        // Set loading state to false
                        handleIntent(HistoryDetailIntent.SetRefreshing(false))
                    }
                }
            }

            is HistoryDetailIntent.DeleteEntry -> {
                AppLog.d(TAG, "Delete entry intent received for entry: ${intent.entry.entry.id}")
                deleteEntryWithUndo(intent.entry)
            }

            is HistoryDetailIntent.SaveNote -> {
                AppLog.d(TAG, "Save note for entry: ${intent.entry.entry.id}")
                saveNote(intent.entry, intent.note)
            }

            is HistoryDetailIntent.SaveBabyEdit -> {
                AppLog.d(TAG, "Save baby edit for entry: ${intent.entry.entry.id}")
                saveBabyEdit(intent)
            }

            else -> Unit
        }
    }

    private fun saveBabyEdit(intent: HistoryDetailIntent.SaveBabyEdit) {
        viewModelScope.launch {
            dialogQueueService.showLoader(HistoryDetailScreenStrings.SaveLoaderMessage)
            try {
                val original = intent.entry
                val updatedBabyEntry = original.babyEntry.copy(
                    babyWeightDecigrams = intent.weightDecigrams,
                    babyLengthMillimeters = intent.lengthMillimeters,
                    entryNote = intent.note,
                    entryType = if (intent.weightDecigrams != null) {
                        BabyEntryType.WEIGHT.value
                    } else {
                        BabyEntryType.MEASURE_LENGTH.value
                    },
                )
                // The date picker re-emits the timestamp from date + hour + minute, dropping the
                // original seconds/millis — so a plain weight/note edit yields a timestamp that
                // differs only in sub-minute precision. Treat the date as CHANGED only when it
                // differs at minute granularity; otherwise a normal edit would wrongly take the
                // delete+recreate path and send operationType=delete instead of edit. (MOB-598)
                val dateChanged = DateTimeConverter.isoToTimestamp(original.entry.entryTimestamp) / MILLIS_PER_MINUTE !=
                    DateTimeConverter.isoToTimestamp(intent.timestamp) / MILLIS_PER_MINUTE
                if (dateChanged) {
                    // Genuine move → the server entryId (babyId_entryType_timestamp) changes, so an
                    // in-place edit would orphan the OLD reading. Delete the original (old entryId)
                    // and create a fresh reading at the new timestamp with a NEW local id (id=0 →
                    // autogen) so the local delete-old and create-new don't collide on a shared id.
                    entryService.deleteEntry(original)
                    entryService.addBabyEntry(
                        BabyEntry(
                            entry = original.entry.copy(id = 0, entryTimestamp = intent.timestamp),
                            babyEntry = updatedBabyEntry.copy(id = 0),
                        ),
                    )
                } else {
                    // Date unchanged → edit in place via operationType=edit (baby-only, §2.16),
                    // keeping the ORIGINAL timestamp so the server entryId is identical and the edit
                    // resolves in place. No delete.
                    entryService.editBabyEntry(
                        BabyEntry(
                            entry = original.entry,
                            babyEntry = updatedBabyEntry,
                        ),
                    )
                }
                handleIntent(HistoryDetailIntent.DismissBabyEditor)
                loadDetail()
            } catch (e: Exception) {
                AppLog.e(TAG, "Error saving baby edit for entry: ${intent.entry.entry.id}", e)
                dialogQueueService.showToast(
                    Toast.Simple(title = null, message = HistoryDetailScreenStrings.NoteSaveError),
                )
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    private fun saveNote(entry: Entry, note: String) {
        viewModelScope.launch {
            dialogQueueService.showLoader(HistoryDetailScreenStrings.SaveLoaderMessage)
            try {
                entryService.updateNote(entry, note.ifBlank { null })
                handleIntent(HistoryDetailIntent.DismissNoteEditor)
                loadDetail()
            } catch (e: Exception) {
                // Keep the editor open and surface the failure instead of closing it as if
                // the save succeeded (MOB-438 PR review).
                AppLog.e(TAG, "Error saving note for entry: ${entry.entry.id}", e)
                dialogQueueService.showToast(
                    Toast.Simple(title = null, message = HistoryDetailScreenStrings.NoteSaveError),
                )
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Deletes an entry optimistically and shows a "Reading deleted." toast with an Undo action
     * (no confirm dialog — the toast is the safety net). Undo restores the reading; a failed
     * delete surfaces a "Couldn't delete!" toast.
     */
    private fun deleteEntryWithUndo(entry: Entry) {
        viewModelScope.launch {
            try {
                entryService.deleteEntry(entry)
                // Best-effort Health Connect mirror — a HC failure must not fail the delete.
                try {
                    healthConnectService.deleteEntry(entry)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to delete entry from Health Connect")
                }
                loadDetail()
                dialogQueueService.showToast(
                    Toast.Simple(
                        message = HistoryDetailScreenStrings.ReadingDeleted,
                        action = ActionButton(
                            text = HistoryDetailScreenStrings.UndoButton,
                            action = { undoDelete(entry) },
                        ),
                    ),
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Error deleting entry: ${entry.entry.id}", e)
                dialogQueueService.showToast(
                    Toast.Simple(
                        title = HistoryDetailScreenStrings.DeleteFailedTitle,
                        message = HistoryDetailScreenStrings.DeleteFailedMessage,
                    ),
                )
            }
        }
    }

    /** Restores a just-deleted reading (Undo) and confirms with a "Reading restored." toast. */
    private fun undoDelete(entry: Entry) {
        viewModelScope.launch {
            try {
                dialogQueueService.dismissToast()
                entryService.restoreEntry(entry)
                loadDetail()
                dialogQueueService.showToast(
                    Toast.Simple(message = HistoryDetailScreenStrings.ReadingRestored),
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Error restoring entry: ${entry.entry.id}", e)
                dialogQueueService.showToast(
                    Toast.Simple(
                        title = HistoryDetailScreenStrings.DeleteFailedTitle,
                        message = HistoryDetailScreenStrings.DeleteFailedMessage,
                    ),
                )
            }
        }
    }

    companion object {
        private const val TAG = "HistoryDetailViewModel"
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
