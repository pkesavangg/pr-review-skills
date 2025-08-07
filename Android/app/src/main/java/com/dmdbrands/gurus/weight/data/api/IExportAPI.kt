package com.dmdbrands.gurus.weight.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API interface for export operations.
 */
interface IExportAPI {
    companion object {
        private const val OPERATION = "operation/"
        private const val CSV = "csv/"
        private const val R4_CSV = "r4/csv/"
    }

    /**
     * Exports CSV data to email for Dashboard 4.
     * @param utcOffset The UTC offset for timezone.
     */
    @GET(OPERATION + CSV)
    suspend fun exportCsvDashboard4(@Query("utcOffset") utcOffset: Int)

    /**
     * Exports CSV data to email for Dashboard 12.
     * @param utcOffset The UTC offset for timezone.
     */
    @GET(OPERATION + R4_CSV)
    suspend fun exportCsvDashboard12(@Query("utcOffset") utcOffset: Int)
}
