package com.dmdbrands.gurus.weight.features.debugMenu.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuIntent
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuReducer
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuState
import com.dmdbrands.gurus.weight.features.debugMenu.strings.DebugMenuStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Debug Menu screen.
 * Handles debug information display and troubleshooting operations.
 * Based on Angular cs-menu.page implementation.
 */
@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val accountService: IAccountService,
    private val entryService: IEntryService,
    private val exportService: IExportService,
    private val logManager: LogManager,
    private val accountFlagService: IAccountFlagService,

) : BaseIntentViewModel<DebugMenuState, DebugMenuIntent>(
    reducer = DebugMenuReducer(),
) {

    private val tag = "DebugMenuViewModel"

    override fun provideInitialState(): DebugMenuState {
        return DebugMenuState(
            appVersion = AppStatusService.version,
            isNative = AppStatusService.isNative,
            isAndroid = AppStatusService.isAndroid,
            apiUrl = AppStatusService.apiUrl,
            currentDateTime = AppStatusService.getCurrentDateTime(),
            timezone = AppStatusService.getUserTimezone(),
            timezoneOffset = AppStatusService.getUserTimezoneOffset(),
            hasScales = false, // TODO: Implement scale detection
            isSendScaleLogEnabled = false, // TODO: Implement based on scale connectivity
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
          is DebugMenuIntent.ShowAppReview -> showAppReviewPrompt()
        }
    }

  private fun showAppReviewPrompt(){
    viewModelScope.launch {
      accountFlagService.launchAppReview()
    }
  }

    /**
     * Handles back navigation.
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
     * Sends scale logs.
     * Based on Angular sendScaleLog() method.
     */
    private fun onSendScaleLogs() {
        viewModelScope.launch {
            dialogQueueService.showLoader(
                message = DebugMenuStrings.Loading.SendLogs,
            )

            try {
                // TODO: Implement scale log sending when scale service is available
                // For now, use a placeholder broadcast ID
                val placeholderBroadcastId = "00:00:00:00:00:00"
                exportService.sendScaleLog(placeholderBroadcastId)

                dialogQueueService.showToast(
                    Toast(
                        message = DebugMenuStrings.Success.LogSent,
                    ),
                )

                AppLog.i(tag, "Scale logs sent successfully")
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to send scale logs", e)
                showErrorAlert()
            } finally {
                dialogQueueService.dismissLoader()
                _state.value = state.value.copy(isLoading = false)
            }
        }
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
                            dialogQueueService.dismissCurrent()
                            navigationService.replaceStack(AppRoute.Auth.Landing)
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
