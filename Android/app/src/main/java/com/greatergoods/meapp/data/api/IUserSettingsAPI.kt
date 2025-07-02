package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.metrics.StreakRequest
import com.greatergoods.meapp.domain.model.api.metrics.WeightlessRequest
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import retrofit2.http.Body
import retrofit2.http.PATCH

/**
 * API interface for user settings endpoints.
 * Handles streak and weightless mode settings operations.
 */
interface IUserSettingsAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val STREAK = "streak"
        private const val WEIGHTLESS = "weightless"
    }

    /**
     * Updates the streak setting for the current user.
     * @param request The streak setting request containing isStreakOn and timestamp
     * @return AccountResponse with updated account data
     */
    @PATCH(ACCOUNT + STREAK)
    suspend fun updateStreak(
        @Body request: StreakRequest,
    ): AccountResponse

    /**
     * Updates the weightless setting for the current user.
     * @param request The weightless setting request containing isWeightlessOn, timestamp, and weight
     * @return AccountResponse with updated account data
     */
    @PATCH(ACCOUNT + WEIGHTLESS)
    suspend fun updateWeightless(
        @Body request: WeightlessRequest,
    ): AccountResponse
}
