package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IExportAPI
import com.dmdbrands.gurus.weight.domain.enums.AccountSettingsAction
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.support.LogEntry
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.export.strings.ExportStrings
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGDeviceLog
import com.dmdbrands.library.ggbluetooth.model.GGDeviceLogResponse
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Service for handling data export operations.
 * Implements functionality similar to the TypeScript ExportService.
 */
@Singleton
class ExportService
@Inject
constructor(
  private val exportAPI: IExportAPI,
  private val accountService: IAccountService,
  private val dialogQueueService: IDialogQueueService,
  private val deviceService: GGDeviceService,
  private val logRepository: ILogRepository,
) : IExportService {
  companion object {
    private const val TAG = "ExportService"
    private const val DASHBOARD_TYPE_12 = "dashboard_12_metrics"
    private const val DASHBOARD_TYPE_4 = "dashboard_4_metrics"
  }

  /**
   * Sends scale logs for debugging purposes.
   * Matches Angular ExportService.sendScaleLog(): getDeviceLogs(broadcastId) then logger.sendScaleLog(scaleLogData).
   *
   * @param broadcastId The Bluetooth broadcast ID of the device.
   */
  override suspend fun sendScaleLog(broadcastId: String) {
    try {
      AppLog.i(TAG, "sendScaleLog - Attempting to send scale logs for device: $broadcastId")

      val device = GGBTDevice(
        name = "",
        broadcastId = broadcastId,
      )
      val deviceLogs = withTimeout(30 * 1000L) {
        suspendCancellableCoroutine { cont ->
          deviceService.getDeviceLogs(device) { response ->
            when (response) {
              is GGDeviceLogResponse.Completed -> cont.resume(response.logs)
              is GGDeviceLogResponse.Fetching -> { /* wait for Completed */ }
            }
          }
        }
      }

      val scaleLogData = buildScaleLogEntries(deviceLogs)
      if (scaleLogData.isEmpty()) {
        AppLog.w(TAG, "sendScaleLog - No device logs to send")
        return
      }
      logRepository.sendScaleLog(scaleLogData)
      AppLog.i(TAG, "sendScaleLog - Scale logs sent successfully")
    } catch (e: Exception) {
      AppLog.e(TAG, "Error sending scale logs", e)
      throw e
    }
  }

  /**
   * Builds list of LogEntry from BLE device logs to match Angular format:
   * first line "Mac Address: &lt;mac&gt;", then each line of each device log as { time: '', data: line }.
   */
  private fun buildScaleLogEntries(deviceLogs: List<GGDeviceLog>): List<LogEntry> {
    val result = mutableListOf<LogEntry>()
    val macAddressInfo = "Mac Address: ${deviceLogs.firstOrNull()?.macAddress ?: ""}"
    result.add(LogEntry(time = "", data = macAddressInfo))
    for (deviceLog in deviceLogs) {
      val logText = deviceLog.log ?: ""
      val logLines = logText.split("\n")
      for (line in logLines) {
        result.add(LogEntry(time = "", data = line))
      }
    }
    return result
  }

  /**
   * Exports CSV data to email with user confirmation prompt.
   * This method should be called from the UI/ViewModel layer which handles the prompt.
   */
  override suspend fun exportCsvWithPrompt() {
    try {
      exportCsvToEmail()
      showExportSuccessToast()
      AppLog.i(TAG, "exportCsvWithPrompt - CSV export completed successfully")
    } catch (e: HttpException) {
      showErrorToast(action = AccountSettingsAction.EXPORT_CSV, e)
      AppLog.e(TAG, "Error during CSV export", e)
      throw e
    }
  }

  /**
   * Exports CSV data to email directly without prompt.
   */
  override suspend fun exportCsvToEmail() {
    try {
      val currentAccount = getCurrentAccount()
      val utcOffset = getUtcOffset()
      // Determine which API endpoint to use based on dashboard type
      if (isDashboard12(currentAccount)) {
        exportAPI.exportCsvDashboard12(utcOffset)
        AppLog.i(TAG, "exportCsvToEmail - CSV sent via Dashboard12 API")
      } else {
        exportAPI.exportCsvDashboard4(utcOffset)
        AppLog.i(TAG, "exportCsvToEmail - CSV sent via Dashboard4 API")
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Error sending CSV to email", e)
      throw e
    }
  }

  /**
   * Gets the current account or throws an exception if no account is available.
   */
  private suspend fun getCurrentAccount(): Account =
    accountService.getCurrentAccount()
      ?: throw IllegalStateException("No current account available")

  /**
   * Calculates the UTC offset in minutes.
   */
  private fun getUtcOffset(): Int {
    val utcOffsetMin = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
    return utcOffsetMin
  }

  /**
   * Determines if the current account uses Dashboard12.
   * @param account The account to check.
   * @return True if Dashboard12, false for Dashboard4.
   */
  private fun isDashboard12(account: Account): Boolean {
    // This would need to be implemented based on your account model
    // For now, returning false as default (Dashboard4)
    return account.dashboardType.equals(DASHBOARD_TYPE_12, ignoreCase = true)
  }

  /**
   * Shows success toast for export operation.
   */
  private fun showExportSuccessToast() {
    dialogQueueService.showToast(
      Toast(
        message = ExportStrings.SuccessMessage,
      ),
    )
  }

  fun showErrorToast(
    action: AccountSettingsAction,
    error: HttpException?,
  ) {
    val message =
      when (action) {
        AccountSettingsAction.EXPORT_CSV -> {
          val message =
            when (error?.code()) {
              HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION ->
                ToastStrings.Error.LoginError.MessageNoConn

              HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR ->
                ToastStrings.Error.LoginError.MessageServError

              HttpErrorConfig.ResponseCode.UNAUTHORIZED ->
                ToastStrings.Error.ExportCsv.Message

              else ->
                ToastStrings.Error.LoginError.MessageGeneric
            }
          message
        }
      }
    val errorToast =
      Toast(
        title = null,
        message = message,
        action = null,
      )
    dialogQueueService.showToast(errorToast)
  }
}
