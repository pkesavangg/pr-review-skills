// data/remote/api/EntryApi.kt
package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesCursorResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesSyncResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import okhttp3.ResponseBody
import retrofit2.Response
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryRequest
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface EntryApi {
    /**
     * Unified entries write (MOB-379): an array body of mixed-category entries
     * sent as an atomic batch. Any non-2xx fails the whole batch (server rolls back).
     */
    @POST("entries/")
    suspend fun postEntries(@Body entries: List<UnifiedEntryRequest>): UnifiedEntryResponse

    @POST("operation/r4")
    suspend fun sendOperation(@Body operation: ScaleApiEntry): ScaleApiEntry

    @GET("operation/r4")
    suspend fun getAllOperations(): OperationsResponse

    @GET("operation/r4")
    suspend fun getOperations(@Query("start") lastUpdated: String): OperationsResponse

    // ── Unified /v3/entries/ read (MOB-380) ────────────────────────────────

    /**
     * Sync mode: returns all entries with `serverTimestamp > start`.
     * No page limit — returns everything since the cursor.
     */
    @GET("entries/")
    suspend fun getEntriesSync(
        @Query("start") start: String,
        @Query("category") category: String? = null,
    ): EntriesSyncResponse

    /**
     * Cursor-pagination mode: returns up to [limit] entries with
     * `entryTimestamp < cursor` (null cursor = newest page first).
     */
    @GET("entries/")
    suspend fun getEntriesPage(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
    ): EntriesCursorResponse

    /**
     * CSV export. `download="true"` returns a file body; omitting it sends email.
     * `babyId` is required by the server when `category=baby` (spec §2.18).
     */
    @Streaming
    @GET("entries/csv")
    suspend fun exportEntriesCsv(
        @Query("category") category: String? = null,
        @Query("babyId") babyId: String? = null,
        @Query("download") download: String? = null,
        @Query("utcOffset") utcOffset: Int = 0,
    ): Response<ResponseBody>
}

data class OperationsResponse(
    val operations: List<ScaleApiEntry>,
    val timestamp: String
)
