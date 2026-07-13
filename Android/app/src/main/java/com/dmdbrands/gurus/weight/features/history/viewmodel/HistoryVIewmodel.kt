package com.dmdbrands.gurus.weight.features.history.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntryCategory
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
  private val entryCursorPager: com.dmdbrands.gurus.weight.data.services.EntryCursorPager,
  private val deviceService: IDeviceService,
  private val productSelectionRepository: IProductSelectionRepository,
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
      // Main.Entry is a bottom-nav tab under the Home top-level backstack, so it must be
      // navigated with Home as the top-level anchor (mirrors HomeViewModel). (MOB-1221)
      is HistoryIntent.OnLogManually ->
        viewModelScope.launch { navigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home) }

      else -> null
    }
  }

  override fun onDependenciesReady() {
    observeAndLoadHistory()
    observeDeviceFlags()
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
  private var deviceJob: Job? = null

  /**
   * Derive per-product device-presence flags so the History empty state can distinguish
   * "no device connected" from "device connected, no entries yet" and show the right copy +
   * CTA per product. (MOB-1221)
   *
   * Re-evaluates whenever the paired-device set changes, but reads presence from the
   * app-standard sources — `IDeviceService.hasWeightScale`, `IProductSelectionManager
   * .hasBabyScaleDevice`, `IProductSelectionRepository.hasBpmDevice` — rather than
   * re-classifying the raw `pairedScales` list by `deviceType`. A baby scale can surface
   * under a weight-scale device type in that raw list (MOB-1175), which would set the wrong
   * flag; using the shared APIs keeps History consistent with Settings/Dashboard. (PR #2242)
   */
  private fun observeDeviceFlags() {
    if (deviceJob != null) return // already observing
    deviceJob = viewModelScope.launch {
      deviceService.pairedScales.collect {
        val accountId = entryReadService.accountId
        handleIntent(
          HistoryIntent.SetDeviceFlags(
            hasWeightDevice = deviceService.hasWeightScale.first(),
            hasBpmDevice = if (accountId.isNullOrEmpty()) {
              false
            } else {
              productSelectionRepository.hasBpmDevice(accountId)
            },
            hasBabyDevice = productSelectionManager.hasBabyScaleDevice.value,
          ),
        )
      }
    }
  }

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
            is GroupedHistory.Baby -> {
              val babyId = (product as? ProductSelection.Baby)?.profile?.id
              if (babyId != null) {
                handleIntent(HistoryIntent.SetBabyHistoryItems(babyId, grouped.weeks))
              }
            }
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
        // If Room is empty after a delta sync, run a full backfill via cursor pager (MOB-380).
        if (state.value.historyItems.isEmpty() && state.value.bpHistoryItems.isEmpty()) {
          AppLog.d(TAG, "Room empty after sync — running cursor backfill")
          val accountId = entryReadService.accountId ?: return@launch
          entryCursorPager.backfill(accountId = accountId)
        }
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

    // Export the active product's own data: map the selection to an entries `category`
    // (weight/bp/baby) and, for baby, the selected babyId (required by the server, spec §2.18).
    val selection = productSelectionManager.selectedProduct.value
    val productType = selection.productType
    val category = EntryCategory.fromProductType(productType).value
    val babyId = (selection as? ProductSelection.Baby)?.profile?.id

    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = ExportStrings.exportDialogTitle(productType),
        message = ExportStrings.ExportDialogMessage,
        confirmText = ExportStrings.SendButton,
        cancelText = ExportStrings.CancelButton,
        onConfirm = {
          performExport(category = category, babyId = babyId)
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
   * Performs the actual export operation with loading and error handling. Emails the CSV for
   * the given [category] (and [babyId] when category=baby) via the unified entries export.
   */
  private fun performExport(category: String, babyId: String?) {
    AppLog.i(TAG, ExportStrings.ExportStarted)

    // Show loading spinner
    dialogQueueService.showLoader(
      message = ExportStrings.LoaderMessage,
    )

    viewModelScope.launch {
      try {
        exportService.exportEntriesCsv(category = category, babyId = babyId, download = false)
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
