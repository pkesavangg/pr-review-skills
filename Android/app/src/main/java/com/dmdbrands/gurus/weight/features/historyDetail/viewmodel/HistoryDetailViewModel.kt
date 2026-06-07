package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.common.helper.AccountHelper.isMetricUnit
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
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
                showDeleteEntryDialog(intent.entry)
            }

            is HistoryDetailIntent.SaveNote -> {
                AppLog.d(TAG, "Save note for entry: ${intent.entry.entry.id}")
                saveNote(intent.entry, intent.note)
            }

            else -> Unit
        }
    }

    private fun saveNote(entry: Entry, note: String) {
        viewModelScope.launch {
            try {
                entryService.updateNote(entry, note.ifBlank { null })
                handleIntent(HistoryDetailIntent.DismissNoteEditor)
                loadDetail()
            } catch (e: Exception) {
                AppLog.e(TAG, "Error saving note for entry: ${entry.entry.id}", e)
                handleIntent(HistoryDetailIntent.DismissNoteEditor)
            }
        }
    }

    private fun showDeleteEntryDialog(entry: Entry) {
        viewModelScope.launch {
            dialogQueueService.showDialog(
                DialogModel.Confirm(
                    title = HistoryDetailScreenStrings.DeleteEntryDialogTitle,
                    message = HistoryDetailScreenStrings.DeleteEntryDialogMessage,
                    confirmText = HistoryDetailScreenStrings.DeleteButton,
                    cancelText = HistoryDetailScreenStrings.CancelButton,
                    primaryActionType = ButtonType.ErrorText,
                    onConfirm = {
                        AppLog.d(TAG, "User confirmed deletion of entry: ${entry.entry.id}")
                        dialogQueueService.showLoader(HistoryDetailScreenStrings.DeleteLoaderMessage)
                        viewModelScope.launch {
                            // Delete from entry service (local + API)
                            entryService.deleteEntry(entry)
                            // Try to delete from Health Connect (non-blocking)
                            try {
                                healthConnectService.deleteEntry(entry)
                                AppLog.d(TAG, "Entry deleted from Health Connect")
                            } catch (e: Exception) {
                                AppLog.w(TAG, "Failed to delete entry from Health Connect")
                                // Don't fail the operation if HC deletion fails
                            }

                            dialogQueueService.dismissCurrent()
                            dialogQueueService.dismissLoader()
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
        }
    }

    companion object {
        private const val TAG = "HistoryDetailViewModel"
    }
}
