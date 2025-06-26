package com.greatergoods.meapp.data.services

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IExportAPI
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.services.IAccountAuthService
import com.greatergoods.meapp.domain.services.IExportService
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling data export operations.
 * Implements functionality similar to the TypeScript ExportService.
 */
@Singleton
class ExportService @Inject constructor(
    private val exportAPI: IExportAPI,
    private val accountAuthService: IAccountAuthService,
) : IExportService {

    companion object {
        private const val TAG = "ExportService"
        private const val DASHBOARD_TYPE_12 = "dashboard_12_metrics"
        private const val DASHBOARD_TYPE_4 = "dashboard_4_metrics"
    }

    /**
     * Sends scale logs for debugging purposes.
     * @param broadcastId The Bluetooth broadcast ID of the device.
     */
    override suspend fun sendScaleLog(broadcastId: String) {
        try {
            AppLog.i(TAG, "sendScaleLog - Attempting to send scale logs for device: $broadcastId")

            // Get device logs (this would require Bluetooth integration)
            // For now, we'll log the request
            AppLog.i(TAG, "sendScaleLog - Scale log request for broadcastId: $broadcastId")

            // In the TypeScript version, this calls getDeviceLogs and then logger.sendScaleLog
            // This would need to be implemented with actual Bluetooth functionality
            // TODO: Implement actual Bluetooth device log retrieval

        } catch (e: Exception) {
            AppLog.e(TAG, "sendScaleLog - Error sending scale logs", e.toString())
            throw e
        }
    }

    /**
     * Exports CSV data to email with user confirmation prompt.
     * This method should be called from the UI/ViewModel layer which handles the prompt.
     */
    override suspend fun exportCsvWithPrompt() {
        try {
            exportCsvToEmail()
            AppLog.i(TAG, "exportCsvWithPrompt - CSV export completed successfully")
        } catch (e: Exception) {
            AppLog.e(TAG, "exportCsvWithPrompt - Error during CSV export", e.toString())
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
            AppLog.e(TAG, "exportCsvToEmail - Error sending CSV to email", e.toString())
            throw e
        }
    }

    /**
     * Gets the current account or throws an exception if no account is available.
     */
    private suspend fun getCurrentAccount(): Account {
        return accountAuthService.getCurrentAccount()
            ?: throw IllegalStateException("No current account available")
    }

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
}
