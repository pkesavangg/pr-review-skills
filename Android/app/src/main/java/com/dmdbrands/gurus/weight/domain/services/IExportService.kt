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

    /**
     * Exports entries via the unified `GET /v3/entries/csv` endpoint (MOB-380).
     * @param category `weight`, `bp`, or null for all.
     * @param download true = stream file to MediaStore Downloads; false = trigger server email.
     */
    suspend fun exportEntriesCsv(category: String? = null, download: Boolean = false)
}
