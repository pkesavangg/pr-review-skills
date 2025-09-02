package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(
    assistedFactory = HistoryDetailViewModel.Factory::class,
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
        AppLog.d(TAG, "HistoryDetailViewModel initialized for month: $month")
        loadHistoryDetail()
    }

    private fun loadHistoryDetail() {
        AppLog.d(TAG, "Loading history details for month: $month")
        viewModelScope.launch {
            try {
                entryService.monthDetails(month).collect { entries ->
                    AppLog.d(TAG, "Received ${entries.size} entries for month: $month")
                    if (entries.isNotEmpty()) {
                        val scaleEntries = entries.filterIsInstance<ScaleEntry>()
                        AppLog.d(TAG, "Filtered to ${scaleEntries.size} scale entries")
                        handleIntent(HistoryDetailIntent.SetHistoryItems(month, scaleEntries))
                    } else {
                        AppLog.w(TAG, "No entries found for month: $month, navigating back")
                        navigationService.navigateBack()
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error loading history details for month: $month", e)
                navigationService.navigateBack()
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
                        entryService.syncOperations()
                        AppLog.d(TAG, "Sync operations completed")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Error during sync operations", e)
                    }
                }
            }

            is HistoryDetailIntent.DeleteEntry -> {
                AppLog.d(TAG, "Delete entry intent received for entry: ${intent.entry.entry.id}")
                showDeleteEntryDialog(intent.entry)
            }

            else -> Unit
        }
    }

    private fun showDeleteEntryDialog(entry: ScaleEntry) {
        AppLog.d(TAG, "Showing delete entry dialog for entry: ${entry.entry.id}")
        viewModelScope.launch {
            try {
                dialogQueueService.showDialog(
                    DialogModel.Confirm(
                        title = HistoryDetailScreenStrings.DeleteEntryDialogTitle,
                        message = HistoryDetailScreenStrings.DeleteEntryDialogMessage,
                        confirmText = HistoryDetailScreenStrings.DeleteButton,
                        cancelText = HistoryDetailScreenStrings.CancelButton,
                        onConfirm = {
                            AppLog.d(TAG, "User confirmed deletion of entry: ${entry.entry.id}")
                            dialogQueueService.showLoader(HistoryDetailScreenStrings.DeleteLoaderMessage)
                            viewModelScope.launch {
                                try {
                                    entryService.deleteEntry(entry)
                                    AppLog.i(TAG, "Successfully deleted entry: ${entry.entry.id}")
                                    dialogQueueService.dismissCurrent()
                                    dialogQueueService.dismissLoader()
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Error deleting entry: ${entry.entry.id}", e)
                                    dialogQueueService.dismissCurrent()
                                    dialogQueueService.dismissLoader()
                                }
                            }
                        },
                        onCancel = {
                            dialogQueueService.dismissCurrent()
                        },
                        onDismiss = {
                            dialogQueueService.dismissCurrent()
                        },
                    ),
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Error showing delete entry dialog", e)
            }
        }
    }

    companion object {
        private const val TAG = "HistoryDetailViewModel"
    }
}
