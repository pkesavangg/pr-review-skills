package com.dmdbrands.gurus.weight.features.historyDetail.viewmodel

import androidx.lifecycle.viewModelScope
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
        loadHistoryDetail()
    }

    private fun loadHistoryDetail() {
        viewModelScope.launch {
            entryService.monthDetails(month).collect {
                if (it.isNotEmpty())
                    handleIntent(HistoryDetailIntent.SetHistoryItems(month, it.filterIsInstance<ScaleEntry>()))
                else
                    navigationService.navigateBack()
            }
        }
    }

    override fun handleIntent(intent: HistoryDetailIntent) {
        super.handleIntent(intent)
        when (intent) {
            is HistoryDetailIntent.Refresh -> {
                viewModelScope.launch {
                    entryService.syncOperations()
                }
            }

            is HistoryDetailIntent.DeleteEntry -> {
                showDeleteEntryDialog(intent.entry)
            }

            else -> Unit
        }
    }

    private fun showDeleteEntryDialog(entry: ScaleEntry) {
        viewModelScope.launch {
            dialogQueueService.showDialog(
                DialogModel.Confirm(
                    title = HistoryDetailScreenStrings.DeleteEntryDialogTitle,
                    message = HistoryDetailScreenStrings.DeleteEntryDialogMessage,
                    confirmText = HistoryDetailScreenStrings.DeleteButton,
                    cancelText = HistoryDetailScreenStrings.CancelButton,
                    onConfirm = {
                        dialogQueueService.showLoader(HistoryDetailScreenStrings.DeleteLoaderMessage)
                        viewModelScope.launch {
                            entryService.deleteEntry(entry)
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
}
