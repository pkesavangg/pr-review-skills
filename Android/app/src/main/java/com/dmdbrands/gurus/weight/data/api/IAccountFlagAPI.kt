package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.review.AccountFlagResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * API interface for account flag endpoints.
 * Handles all account flag related network operations.
 */
interface IAccountFlagAPI {
    companion object {
        private const val ACCOUNT_FLAG = "account/flag"
    }

    /**
     * Gets all account flags for the current user.
     * @return List of account flags
     */
    @GET(ACCOUNT_FLAG)
    suspend fun getAccountFlags(): List<AccountFlagResponse>

    /**
     * Deletes a specific account flag by ID.
     * @param flagId The ID of the flag to delete
     * @return Boolean indicating success
     */
    @DELETE("$ACCOUNT_FLAG/{flagId}")
    suspend fun deleteAccountFlag(@Path("flagId") flagId: String): Boolean
}
