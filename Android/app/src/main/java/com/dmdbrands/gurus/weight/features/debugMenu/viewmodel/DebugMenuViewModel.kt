package com.dmdbrands.gurus.weight.features.debugMenu.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.SCALES
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuIntent
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuReducer
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuState
import com.dmdbrands.gurus.weight.features.debugMenu.strings.DebugMenuStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import android.app.Activity

/**
 * ViewModel for the Debug Menu screen.
 * Handles debug information display and troubleshooting operations.
 * Based on Angular cs-menu.page implementation.
 */
@HiltViewModel
class DebugMenuViewModel @Inject constructor(
  private val accountService: IAccountService,
  private val deviceService: IDeviceService,
  private val entryService: IEntryService,
  private val exportService: IExportService,
  private val logManager: LogManager,
  private val appReviewManager: IAppReviewManager,

  ) : BaseIntentViewModel<DebugMenuState, DebugMenuIntent>(
    reducer = DebugMenuReducer(),
) {

  // Scale-related properties similar to Angular cs-menu.page.ts
  private var scales: List<Device> = emptyList()
  private var singularScale: Device? = null

  init {
      viewModelScope.launch {
        deviceService.pairedScales.collect { pairedScales ->
          scales = pairedScales
            .filter { device ->
              val scaleInfo = getScaleInfoBySku(device.getSKU())
              scaleInfo?.setupType == com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType.BtWifiR4
            }
            .map { device ->
              val scaleInfo = getScaleInfoBySku(device.getSKU())
              device.copy(
                sku = scaleInfo?.sku ?: device.getSKU(),
              )
            }

          singularScale = if (scales.size == 1) scales[0] else null

          // Update state via intent so reducer sorts scaleList by createdAt (like AddScale SetSavedScales)
          handleIntent(DebugMenuIntent.SetScaleList(scales))
        }
      }
  }

    private val tag = "DebugMenuViewModel"

    /**
     * Get scale info by SKU, similar to Angular scaleInfoService.getScaleInfoBySku()
     */
    private fun getScaleInfoBySku(sku: String): ScaleInfo? {
        return SCALES.find { it.sku == sku }
    }


    /**
     * Check if send scale log is enabled, similar to Angular isSendScaleLogEnabled getter
     */
    private fun isSendScaleLogEnabled(): Boolean {
        return singularScale?.connectionStatus == com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus.CONNECTED || scales.size > 1
    }

    override fun provideInitialState(): DebugMenuState {
        return DebugMenuState(
            appVersion = AppStatusService.version,
            isNative = AppStatusService.isNative,
            isAndroid = AppStatusService.isAndroid,
            apiUrl = AppStatusService.apiUrl,
            currentDateTime = AppStatusService.getCurrentDateTime(),
            timezone = AppStatusService.getUserTimezone(),
            timezoneOffset = AppStatusService.getUserTimezoneOffset(),
        )
    }

    override fun handleIntent(intent: DebugMenuIntent) {
        super.handleIntent(intent)
        when (intent) {
            is DebugMenuIntent.OnBack -> onBack()
            is DebugMenuIntent.SendLogs -> onSendLogs()
            is DebugMenuIntent.ResyncEntries -> onResyncEntries()
            is DebugMenuIntent.ClearAllData -> onClearAllData(intent.onDismiss)
            is DebugMenuIntent.SendScaleLogs -> onSendScaleLogs()
            is DebugMenuIntent.SendScaleLogForScale -> onSendScaleLogForScale(intent.device)
            is DebugMenuIntent.ShowAppReview -> showAppReviewPrompt(null)
            is DebugMenuIntent.ShowAppReviewWithActivity -> showAppReviewPrompt(intent.activity)
          else -> {}
        }
    }

  private fun showAppReviewPrompt(activity: Activity?){
    viewModelScope.launch {
      try {
        AppLog.i(tag, "Launching app review flow")
        // Use the launchInAppReview method which takes a Context
        activity?.let {
          appReviewManager.launchInAppReview(it)
          AppLog.i(tag, "App review flow launched successfully")
        } ?: run {
          AppLog.w(tag, "Activity is null, cannot launch app review")
          showErrorAlert()
        }
      } catch (e: Exception) {
        AppLog.e(tag, "Error launching app review flow ${e}", e)
        showErrorAlert()
      }
    }
  }

    /**
     * Handles back navigation from Debug Menu screen.
     */
    private fun onBack() {
        viewModelScope.launch {
            try {
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to navigate back from debug menu", e)
            }
        }
    }

    /**
     * Sends application logs for the current active account.
     * Based on Angular sendLog() method.
     */
    private fun onSendLogs() {
        viewModelScope.launch {
            dialogQueueService.showLoader(
                message = DebugMenuStrings.Loading.SendLogs,
            )
            try {
                val activeAccount = accountService.activeAccountFlow.first()
                activeAccount?.let { account ->
                    AppLog.i(tag, "Sending logs for account: ${account.id}")
                    // Send logs using LogManager (now account-specific)
                    logManager.sendLogs()
                    dialogQueueService.showToast(
                        Toast(
                            message = DebugMenuStrings.Success.LogSent,
                        ),
                    )
                    AppLog.i(tag, "App logs sent successfully for account: ${account.id}")
                } ?: run {
                    AppLog.w(tag, "No active account found for sending logs")
                    showErrorAlert()
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to send app logs", e)
                showErrorAlert()
            } finally {
                dialogQueueService.dismissLoader()
                _state.value = state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Resyncs entries by clearing local data and syncing from server.
     * Based on Angular resyncEntries() method.
     */
    private fun onResyncEntries() {
        viewModelScope.launch {
            dialogQueueService.showLoader(
                message = DebugMenuStrings.Loading.Resync,
            )

            try {
                val activeAccount = accountService.activeAccountFlow.first()
                activeAccount?.let { account ->
                    // Sync operations (equivalent to Angular's operationService.syncOperations())
                    entryService.syncOperations()
                    dialogQueueService.showToast(
                        Toast(
                            message = DebugMenuStrings.Success.Synced,
                        ),
                    )
                }

                AppLog.i(tag, "Entries resynced successfully")
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to resync entries", e)
                showErrorAlert()
            } finally {
                dialogQueueService.dismissLoader()
                _state.value = state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Clears all local data for the current active account including logs and DataStore.
     * Based on Angular clearAllData() method.
     */
    private fun onClearAllData(onDismiss: () -> Unit) {
        viewModelScope.launch {
            dialogQueueService.showLoader(
                message = DebugMenuStrings.Loading.PleaseWait,
            )

            try {
                val activeAccount = accountService.activeAccountFlow.first()
                if(activeAccount !== null){
                    accountService.reset()
                }
                showRestartAlert(onDismiss)
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to clear all data", e)
                showErrorAlert()
            }
            finally {
                dialogQueueService.dismissLoader()
                _state.value = state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Sends scale logs. Matches Ionic cs-menu sendScaleLog():
     * - Single scale: show loader, send, toast on success; on error show restart alert if not network error.
     */
    private fun onSendScaleLogs() {
        viewModelScope.launch {
            when {
                singularScale != null -> {
                    dialogQueueService.showLoader(message = DebugMenuStrings.Loading.SendScaleLogs)
                    try {
                        exportService.sendScaleLog(singularScale!!.getBroadcastIdString())
                        dialogQueueService.showToast(Toast(message = DebugMenuStrings.Success.LogSent))
                        AppLog.i(tag, "Scale logs sent for singular scale")
                    } catch (e: Exception) {
                        AppLog.e(tag, "Failed to send scale logs", e)
                        if (!isNetworkError(e)) {
                            showRestartAlertForScaleLog()
                        }
                    } finally {
                        dialogQueueService.dismissLoader()
                        _state.value = state.value.copy(isLoading = false)
                    }
                }
                scales.size > 1 -> {
                    navigationService.navigateTo(
                        AppRoute.AccountSettings.ScaleLogsPicker
                    )
                }
                else -> {
                    AppLog.w(tag, "No scales available for sending logs")
                    showErrorAlert()
                }
            }
        }
    }

    /**
     * Sends scale log for the device selected from the scale picker.
     * Matches Ionic ScaleLogsModalComponent onClick flow. On success navigates back to Debug Menu.
     */
    private fun onSendScaleLogForScale(device: Device) {
        viewModelScope.launch {
            if (device.connectionStatus != com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus.CONNECTED) {
                AppLog.w(tag, "Selected scale not connected, skipping send")
                _state.value = state.value.copy(
                    scaleLogsPickerScales = emptyList(),
                    isLoading = false,
                )
                return@launch
            }
            dialogQueueService.showLoader(message = DebugMenuStrings.Loading.SendScaleLogs)
            try {
                exportService.sendScaleLog(device.getBroadcastIdString())
                dialogQueueService.showToast(Toast(message = DebugMenuStrings.Success.LogSent))
                AppLog.i(tag, "Scale logs sent for scale from picker")
                navigationService.navigateBack()
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to send scale logs for selected scale", e)
                if (!isNetworkError(e)) {
                    showRestartAlertForScaleLog()
                }
                _state.value = state.value.copy(
                    scaleLogsPickerScales = emptyList(),
                )
            } finally {
                dialogQueueService.dismissLoader()
                _state.value = state.value.copy(isLoading = false)
            }
        }
    }

    /** Returns true if the throwable is likely a network/connectivity error (like Ionic checkInternetError). */
    private fun isNetworkError(e: Throwable): Boolean {
        return e is java.io.IOException ||
            e is java.net.UnknownHostException ||
            e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException ||
            (e is HttpException && e.code() == 0)
    }

    /** Restart alert for scale log errors (matches Ionic loggerMessage / menuHeader). */
    private fun showRestartAlertForScaleLog() {
        dialogQueueService.enqueue(
            DialogModel.Alert(
                title = DebugMenuStrings.Alerts.ErrorHeader,
                message = DebugMenuStrings.Alerts.ErrorMessage,
                onDismiss = {},
            ),
        )
    }

    /**
     * Shows error alert dialog.
     */
    private fun showErrorAlert() {
        dialogQueueService.enqueue(
            DialogModel.Alert(
                title = DebugMenuStrings.Alerts.ErrorHeader,
                message = DebugMenuStrings.Alerts.ErrorMessage,
                onDismiss = {},
            ),
        )
    }

    /**
     * Shows restart alert dialog.
     * Based on Angular showRestartAlert() method.
     */
    private fun showRestartAlert(onDismiss: () -> Unit) {
        dialogQueueService.enqueue(
            DialogModel.Alert(
                title = DebugMenuStrings.Alerts.DataHeader,
                message = DebugMenuStrings.Alerts.DataMessage,
                dismissText = DebugMenuStrings.Ok,
                onDismiss = {
                    AppLog.i(tag, "Restart alert confirmed, executing app exit callback")
                    viewModelScope.launch {
                        try {
                            navigationService.emitAuthEvent(AuthState.LoggedOut(true))
                            dialogQueueService.dismissCurrent()
                            onDismiss.invoke()
                            AppLog.i(tag, "App exit callback executed successfully")
                        } catch (e: Exception) {
                            AppLog.e(tag, "Error executing app exit callback", e)
                        }
                    }
                },
            ),
        )
    }
}
