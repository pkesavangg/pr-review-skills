package com.dmdbrands.gurus.weight.domain.services

/**
 * Service interface for export operations.
 */
interface IExportService {
    /**
     * Sends scale logs for debugging purposes.
     * @param broadcastId The Bluetooth broadcast ID of the device.
     */
    suspend fun sendScaleLog(broadcastId: String)

    /**
     * Exports CSV data to email with user confirmation prompt.
     */
    suspend fun exportCsvWithPrompt()

    /**
     * Exports CSV data to email directly without prompt.
     */
    suspend fun exportCsvToEmail()
}
