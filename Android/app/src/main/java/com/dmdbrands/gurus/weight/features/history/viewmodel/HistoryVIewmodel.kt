package com.dmdbrands.gurus.weight.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import kotlinx.coroutines.flow.collectLatest
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import kotlinx.coroutines.Job
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
  private val entryReadService: IEntryReadService,
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

      is HistoryIntent.Export -> {
        onExportDataClick()
      }
      is HistoryIntent.OnConnectScale -> navigateTo(AppRoute.AccountSettings.MyDevices)

      else -> null
    }
  }

  override fun onDependenciesReady() {
    observeAndLoadHistory()
  }

  init {
    viewModelScope.launch {
      entryService.isUpdating.collect {
        handleIntent(HistoryIntent.Loading(it))
      }
    }
  }

  private val historyJobs = mutableListOf<Job>()
  private var observeJob: Job? = null

  /**
   * Start observing availableProducts. When products change,
   * cancels previous history collectors and reloads all.
   * Called once from screen's LaunchedEffect.
   */
  fun observeAndLoadHistory() {
    if (observeJob != null) return // already observing
    observeJob = viewModelScope.launch {
      productSelectionManager.availableProducts.collectLatest { products ->
        loadAllHistory(products)
      }
    }
  }

  private fun loadAllHistory(availableProducts: List<ProductSelection>) {
    if (entryReadService.accountId == null) return

    // Cancel previous collectors
    historyJobs.forEach { it.cancel() }
    historyJobs.clear()

    // Load each available product
    availableProducts.forEach { product ->
      historyJobs += viewModelScope.launch {
        AppLog.d(TAG, "Loading history for ${product.productType}")
        entryReadService.getGroupedHistory(product).collect { grouped ->
          when (grouped) {
            is GroupedHistory.Weight -> handleIntent(HistoryIntent.SetHistoryItems(grouped.months))
            is GroupedHistory.BloodPressure -> handleIntent(HistoryIntent.SetBpHistoryItems(grouped.months))
            is GroupedHistory.Baby -> handleIntent(HistoryIntent.SetBabyHistoryItems(grouped.weeks))
          }
        }
      }
    }
  }

  private fun resync() {
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "History resync started")
        entryService.syncOperations()
        AppLog.i(TAG, "History resync completed")
      } catch (e: Exception) {
        AppLog.e(TAG, "History resync failed", e)
      }
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
