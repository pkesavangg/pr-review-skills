package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordRequest
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.DashboardMetricsRequest
import com.dmdbrands.gurus.weight.domain.model.api.dashboard.DashboardTypeRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.BodyCompRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.PUT

interface IUserAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val PROFILE = "profile"
        private const val BODY_COMP = "bodycomp"
        private const val NOTIFICATION = "notification"
        private const val DASHBOARD_TYPE = "dashboard-type"
        private const val DASHBOARD_METRICS = "dashboard-metrics"
        private const val PASSWORD = "password"
    }

    @PATCH(ACCOUNT + PROFILE)
    suspend fun updateProfile(
        @Body request: ProfileUpdateRequest,
    ): AccountResponse

    @PATCH(ACCOUNT + BODY_COMP)
    suspend fun updateBodyComp(
        @Body request: BodyCompRequest,
    ): AccountResponse

    @PATCH(ACCOUNT + DASHBOARD_TYPE)
    suspend fun updateDashboardType(
        @Body request: DashboardTypeRequest,
    ): AccountResponse

    @PATCH(ACCOUNT + DASHBOARD_METRICS)
    suspend fun updateDashboardMetrics(
        @Body request: DashboardMetricsRequest,
    ): AccountResponse

    @DELETE(ACCOUNT)
    suspend fun deleteAccount(): Unit

    /**
     * Changes user password.
     * @param request ChangePasswordRequest containing old and new passwords.
     * @return Response indicating success or failure.
     */
    @PUT(ACCOUNT + PASSWORD)
    suspend fun changePassword(
        @Body request: ChangePasswordRequest,
    ): ChangePasswordResponse
}
