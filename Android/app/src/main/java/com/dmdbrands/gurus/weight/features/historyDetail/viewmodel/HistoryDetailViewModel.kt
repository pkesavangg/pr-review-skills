package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.di.ApplicationScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.enums.BabyEntryType
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntrySource
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(
    assistedFactory = HistoryDetailViewModel.Factory::class,
)
class HistoryDetailViewModel @AssistedInject constructor(
    private val accountService: IAccountService,
    private val entryService: IEntryService,
    private val healthConnectService: IHealthConnectService,
    private val entryReadService: IEntryReadService,
    private val userDataStore: UserDataStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @Assisted val month: String,
    @Assisted val productType: ProductType,
) : BaseIntentViewModel<HistoryDetailState, HistoryDetailIntent>(HistoryDetailReducer()) {

    /** Cancellable commit timers per swipe-deleted entry id (the Undo window). */
    // Touched from the main thread (swipe-delete / undo / retry UI callbacks) and from an IO
    // coroutine (commitDelete's cleanup on @ApplicationScope). ConcurrentHashMap makes those
    // structural mutations thread-safe — a plain map risks ConcurrentModificationException or a
    // lost timer entry. Check-then-act sequences all run on Main, so per-op atomicity suffices.
    private val pendingDeleteJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

    /** The active detail collection; cancelled+relaunched on every [loadDetail] so live-flow
     *  collectors never stack when a mutation forces a refresh (MOB-1173). */
    private var detailJob: Job? = null

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
        // Baby entries render in the My Kids unit (LB_OZ / LB / KG) — needed to tell decimal-lb
        // from lb-oz, which isMetric alone can't. (MOB-1499)
        if (productType == ProductType.BABY) {
            viewModelScope.launch {
                userDataStore.babyWeightUnitForCurrentAccountFlow
                    .distinctUntilChanged()
                    .collect { handleIntent(HistoryDetailIntent.SetBabyWeightUnit(it)) }
            }
        }
        loadDetail()
    }

    private fun loadDetail() {
        val product = productSelectionManager.selectedProduct.value
        AppLog.d(TAG, "Loading ${product.productType} details for key: $month")
        // Baby day-detail is keyed by the day (YYYY-MM-DD); show the birthday balloon in the header
        // when that day is the baby's birth date.
        val babyBirthdate = (product as? ProductSelection.Baby)?.profile?.birthdate
        handleIntent(HistoryDetailIntent.SetBirthdayBalloon(DateTimeConverter.isBirthDate(month, babyBirthdate)))
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
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
                    } else if (state.value.historyItems.isNotEmpty()) {
                        // The last entry in this detail was just deleted — nothing left to show, so
                        // return to the History screen (MOB-1173).
                        AppLog.d(TAG, "Last entry removed for key: $month — navigating back to History")
                        navigationService.navigateBack(topLevel = null)
                    } else {
                        // Initial empty is a valid state, not an error — clear the list. (MOB-1462)
                        AppLog.d(TAG, "No entries for key: $month — clearing list")
                        handleIntent(HistoryDetailIntent.SetHistoryItems(month, emptyList()))
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
                confirmDelete(intent.entry)
            }

            is HistoryDetailIntent.SaveBabyEdit -> {
                AppLog.d(TAG, "Save baby edit for entry: ${intent.entry.entry.id}")
                saveBabyEdit(intent)
            }

            is HistoryDetailIntent.SaveWeightEdit -> {
                AppLog.d(TAG, "Save weight edit for entry: ${intent.original.entry.id}")
                saveWeightEdit(intent)
            }

            is HistoryDetailIntent.SaveBpEdit -> {
                AppLog.d(TAG, "Save BP edit for entry: ${intent.original.entry.id}")
                saveBpEdit(intent)
            }

            else -> Unit
        }
    }

    /**
     * Persists a BP edit in place via operationType=edit on the unified /v3/entries/ endpoint
     * (MOB-1173). Manual readings carry edited systolic/diastolic/pulse + note; device-synced
     * readings only change the note (values were disabled in the sheet, so they're unchanged) —
     * either way it's a single in-place edit, and BP notes DO sync (unlike weight). A date change
     * returns to the History screen.
     */
    private fun saveBpEdit(intent: HistoryDetailIntent.SaveBpEdit) {
        viewModelScope.launch {
            dialogQueueService.showLoader(HistoryDetailScreenStrings.SaveLoaderMessage)
            try {
                val dateChanged = isDateChanged(intent.original.entry.entryTimestamp, intent.updated.entry.entryTimestamp)
                entryService.editEntry(intent.updated)
                handleIntent(HistoryDetailIntent.DismissBpEditor)
                finishEditNavigation(dateChanged)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error saving BP edit for entry: ${intent.original.entry.id}", e)
                dialogQueueService.showToast(
                    Toast.Simple(title = null, message = HistoryDetailScreenStrings.NoteSaveError),
                )
            } finally {
                dialogQueueService.dismissLoader()
            }
        }
    }

    /**
     * Persists a weight edit from the weight edit sheet (MOB-1173), branching on the reading's
     * source:
     * - MANUAL → values + note are editable. Edited IN PLACE via operationType=edit on the unified
     *   /v3/entries/ endpoint ([IEntryService.editEntry]); the API supports edit for weight/BP/baby
     *   (Me App 2.0 API spec §2.16). [intent].updated keeps the original row's identity (id,
     *   serverTimestamp, device fields) with the edited values/note/timestamp applied.
     * - DEVICE-SYNCED → only the note is editable (values came from the device and stay read-only),
     *   so persist the note in place via [IEntryService.updateNote].
     */
    private fun saveWeightEdit(intent: HistoryDetailIntent.SaveWeightEdit) {
        viewModelScope.launch {
            dialogQueueService.showLoader(HistoryDetailScreenStrings.SaveLoaderMessage)
            try {
                val original = intent.original
                val dateChanged = isDateChanged(original.entry.entryTimestamp, intent.updated.entry.entryTimestamp)
                if (original.scale.scaleEntry.source == EntrySource.MANUAL.value) {
                    entryService.editEntry(intent.updated)
                } else {
                    entryService.updateNote(original, intent.updated.scale.scaleEntry.note?.ifBlank { null })
                }
                handleIntent(HistoryDetailIntent.DismissWeightEditor)
                finishEditNavigation(dateChanged)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error saving weight edit for entry: ${intent.original.entry.id}", e)
                dialogQueueService.showToast(
                    Toast.Simple(title = null, message = HistoryDetailScreenStrings.NoteSaveError),
                )
            } finally {
                dialogQueueService.dismissLoader()
            }
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
                val dateChanged = isDateChanged(original.entry.entryTimestamp, intent.timestamp)
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
                finishEditNavigation(dateChanged)
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


    /**
     * True when [newTimestamp] differs from [originalTimestamp] at MINUTE granularity. The date
     * picker re-emits the timestamp from date + hour + minute, dropping the original seconds/millis,
     * so a values/note-only edit differs only in sub-minute precision — comparing at minutes avoids
     * treating it as a move. (MOB-598 / MOB-1173)
     */
    private fun isDateChanged(originalTimestamp: String, newTimestamp: String): Boolean =
        DateTimeConverter.isoToTimestamp(originalTimestamp) / MILLIS_PER_MINUTE !=
            DateTimeConverter.isoToTimestamp(newTimestamp) / MILLIS_PER_MINUTE

    /**
     * After an edit save: when the date changed the entry may have moved off this month-scoped
     * screen (whose headline is the month), so return to the History screen; otherwise reload the
     * current month in place. (MOB-1173)
     */
    private suspend fun finishEditNavigation(dateChanged: Boolean) {
        if (dateChanged) {
            navigationService.navigateBack(topLevel = null)
        } else {
            loadDetail()
        }
    }

    /**
     * Shows the "Delete this record?" confirmation alert (Figma 29833-120461) before deleting.
     * Shared by all three products (weight / BP / baby) via the [HistoryDetailIntent.DeleteEntry]
     * intent. Only on confirm does the deferred delete begin.
     */
    private fun confirmDelete(entry: Entry) {
        dialogQueueService.enqueue(
            DialogModel.Confirm(
                title = HistoryDetailScreenStrings.DeleteEntryDialogTitle,
                message = HistoryDetailScreenStrings.DeleteEntryDialogMessage,
                confirmText = HistoryDetailScreenStrings.DeleteButton,
                cancelText = HistoryDetailScreenStrings.CancelButton,
                // Destructive action — red "DELETE" (matches the swipe action + mock).
                primaryActionType = ButtonType.ErrorText,
                onConfirm = { deleteEntryWithUndo(entry) },
            ),
        )
    }

    /**
     * Deferred delete with Undo. The row is HIDDEN immediately (added to `pendingDeleteIds`) but the
     * entry is NOT touched in the DB yet, and a "Reading deleted · Undo" toast is shown. The actual
     * delete is committed only after the Undo window elapses — so Undo is a pure un-hide (no
     * re-create; identical for weight/BP/baby). The commit runs on the app scope so it still
     * completes if the user leaves the screen before the window ends.
     */
    private fun deleteEntryWithUndo(entry: Entry) {
        val id = entry.entry.id
        pendingDeleteJobs[id]?.cancel()
        pendingDeleteJobs[id] = appScope.launch {
            // Persist the hide (row filtered out by entry_view) so it survives process death; commit
            // only after the Undo window elapses. If the app is killed mid-window the flag persists
            // and the launch-flush commits it.
            entryService.setPendingDelete(entry, true)
            // Re-query so the row disappears immediately (entry_view now filters it out); the live
            // flow's re-emit isn't relied on here — loadDetail is the VM's established refresh path.
            withContext(Dispatchers.Main.immediate) { loadDetail() }
            delay(UNDO_WINDOW_MS)
            commitDelete(entry)
        }
        dialogQueueService.showToast(
            Toast.Simple(
                message = HistoryDetailScreenStrings.ReadingDeleted,
                action = ActionButton(
                    text = HistoryDetailScreenStrings.UndoButton,
                    action = { undoDelete(entry) },
                ),
            ),
        )
    }

    /** Undo: cancel the pending commit and clear the pending-delete flag — nothing was ever deleted. */
    private fun undoDelete(entry: Entry) {
        val id = entry.entry.id
        pendingDeleteJobs.remove(id)?.cancel()
        appScope.launch {
            entryService.setPendingDelete(entry, false)
            // Bring the row back, then confirm the restore. Shown from the async block so it lands
            // AFTER the ToastCard's trailing clearToast() (which dismisses the "Reading deleted." toast).
            withContext(Dispatchers.Main.immediate) {
                loadDetail()
                dialogQueueService.showToast(
                    Toast.Simple(
                        message = HistoryDetailScreenStrings.ReadingRestored,
                        icon = AppIcons.Default.Restore,
                    ),
                )
            }
        }
    }

    /**
     * Commits the delete once the Undo window elapses: soft-deletes locally (clearing the pending
     * flag) + syncs, and mirrors to Health Connect (best-effort). On failure the pending flag is
     * cleared so the row reappears, and an error toast is shown.
     */
    private suspend fun commitDelete(entry: Entry) {
        val id = entry.entry.id
        try {
            entryService.deleteEntry(entry)
            try {
                healthConnectService.deleteEntry(entry)
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to delete entry from Health Connect")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error deleting entry: $id", e)
            entryService.setPendingDelete(entry, false)
            withContext(Dispatchers.Main.immediate) {
                loadDetail()
                dialogQueueService.showToast(
                    Toast.Simple(
                        message = HistoryDetailScreenStrings.DeleteFailedTitle,
                        icon = AppIcons.Default.Delete,
                        isError = true,
                        action = ActionButton(
                            text = HistoryDetailScreenStrings.TryAgainButton,
                            action = { retryDelete(entry) },
                        ),
                    ),
                )
            }
        } finally {
            pendingDeleteJobs.remove(id)
        }
    }

    /**
     * "TRY AGAIN" from the delete-failed toast: re-hide the row and re-attempt the commit
     * immediately (no fresh Undo window — the user already confirmed).
     */
    private fun retryDelete(entry: Entry) {
        val id = entry.entry.id
        pendingDeleteJobs[id]?.cancel()
        pendingDeleteJobs[id] = appScope.launch {
            entryService.setPendingDelete(entry, true)
            withContext(Dispatchers.Main.immediate) { loadDetail() }
            commitDelete(entry)
        }
    }

    companion object {
        private const val TAG = "HistoryDetailViewModel"
        private const val MILLIS_PER_MINUTE = 60_000L

        /**
         * Undo window before a swipe-delete is committed — matched to the toast's auto-dismiss
         * (ToastHandler.AUTO_DISMISS_MS = 3900ms) so the delete commits as the toast disappears.
         */
        private const val UNDO_WINDOW_MS = 3900L
    }
}
