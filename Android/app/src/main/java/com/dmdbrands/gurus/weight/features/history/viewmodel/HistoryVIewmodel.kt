package com.dmdbrands.gurus.weight.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.export.strings.ExportStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
  private val entryService: IEntryService,
  private val exportService: IExportService,
) : BaseIntentViewModel<HistoryState, HistoryIntent>(
  HistoryReducer(),
) {
  private val TAG = "HistoryViewModel"
  override fun provideInitialState(): HistoryState = HistoryState()

  override fun handleIntent(intent: HistoryIntent) {
    super.handleIntent(intent)
    when (intent) {
      is HistoryIntent.Refresh -> {
        resync()
      }

      is HistoryIntent.getHistory -> {
        viewModelScope.launch {
          entryService.monthDetails(intent.start).collect {
          }
        }
      }

      is HistoryIntent.Export -> {
        onExportDataClick()
      }
      is HistoryIntent.OnConnectScale -> navigateTo(AppRoute.AccountSettings.AddEditScales)

      else -> null
    }
  }

  init {
    loadHistory()
    viewModelScope.launch {
      entryService.isUpdating.collect {
        handleIntent(HistoryIntent.Loading(it))
      }
    }
  }

  private fun loadHistory() {
    viewModelScope.launch {
      entryService.monthlyAverage.collect { items ->
        handleIntent(
          HistoryIntent.SetHistoryItems(
            items = items,
          ),
        )
      }
    }
  }

  private fun resync() {
    viewModelScope.launch {
      entryService.syncOperations()
    }
  }

  /**
   * Handles export data click by showing confirmation dialog.
   */
  fun onExportDataClick() {
    AppLog.d(TAG, "Export data clicked")

    // Show confirmation dialog
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ExportStrings.ExportDialogTitle,
        message = ExportStrings.ExportDialogMessage,
        confirmText = ExportStrings.SendButton,
        cancelText = ExportStrings.CancelButton,
        onConfirm = {
          performExport()
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          AppLog.d(TAG, "User cancelled export")
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Performs the actual export operation with loading and error handling.
   */
  private fun performExport() {
    AppLog.i(TAG, ExportStrings.ExportStarted)

    // Show loading spinner
    dialogQueueService.showLoader(
      message = ExportStrings.LoaderMessage,
    )

    viewModelScope.launch {
      try {
        exportService.exportCsvWithPrompt()
        AppLog.i(TAG, ExportStrings.ExportCompleted)
      } catch (e: Exception) {
        AppLog.e(TAG, ExportStrings.ExportFailed, e)
      } finally {
        dialogQueueService.dismissLoader()
      }
    }
  }

  fun navigateTo(route: AppRoute) {
    viewModelScope.launch {
      navigationService.navigateTo(route)
    }
  }
}
