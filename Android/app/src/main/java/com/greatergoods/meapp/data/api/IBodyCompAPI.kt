package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import retrofit2.http.Body
import retrofit2.http.PATCH

/**
 * API interface for body composition endpoints.
 * Handles body composition related API calls including activity level and unit updates.
 */
interface IBodyCompAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val BODYCOMP = "bodycomp"
    }

    /**
     * Updates body composition settings including activity level, weight unit, and height.
     * @param bodyCompData The body composition data to update
     * @return AccountResponse with updated account information
     */
    @PATCH(ACCOUNT + BODYCOMP)
    suspend fun updateBodyComp(@Body bodyCompData: BodyCompUpdateRequest): AccountResponse
}
