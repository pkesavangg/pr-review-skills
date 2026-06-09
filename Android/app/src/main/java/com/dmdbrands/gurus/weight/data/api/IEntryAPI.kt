// data/remote/api/EntryApi.kt
package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryRequest
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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
}

data class OperationsResponse(
    val operations: List<ScaleApiEntry>,
    val timestamp: String
)
