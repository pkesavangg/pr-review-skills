// data/remote/api/EntryApi.kt
package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.entry.ScaleEntry
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EntryApi {
    @POST("operation/r4")
    suspend fun sendOperation(@Body operation: ScaleEntry): ScaleEntry

    @GET("operation/r4")
    suspend fun getAllOperations(): OperationsResponse

    @GET("operation/r4")
    suspend fun getOperations(@Query("start") lastUpdated: Long): OperationsResponse
}

data class OperationsResponse(
    val operations: List<ScaleEntry>,
    val timestamp: Long
)
