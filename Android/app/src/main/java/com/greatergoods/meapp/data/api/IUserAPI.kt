package com.greatergoods.meapp.data.api

import StreakRequest
import WeightlessRequest
import com.greatergoods.meapp.domain.model.api.*
import com.greatergoods.meapp.domain.model.api.auth.LoginResponse
import com.greatergoods.meapp.domain.model.api.dashboard.DashboardMetricsRequest
import com.greatergoods.meapp.domain.model.api.dashboard.DashboardTypeRequest
import com.greatergoods.meapp.domain.model.api.metrics.BodyCompRequest
import com.greatergoods.meapp.domain.model.api.user.CreateAccountRequest
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import retrofit2.http.*

interface IUserAPI {
    companion object {
        private const val ACCOUNT = "account/"
        private const val PROFILE = "profile"
        private const val BODY_COMP = "bodycomp"
        private const val WEIGHTLESS = "weightless"
        private const val NOTIFICATION = "notification"
        private const val STREAK = "streak"
        private const val DASHBOARD_TYPE = "dashboard-type"
        private const val DASHBOARD_METRICS = "dashboard-metrics"
    }

    @POST(ACCOUNT)
    suspend fun createAccount(@Body request: CreateAccountRequest): LoginResponse

    @GET(ACCOUNT)
    suspend fun getAccount(): AccountResponse

    @PATCH(ACCOUNT + PROFILE)
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): AccountResponse

    @PATCH(ACCOUNT + BODY_COMP)
    suspend fun updateBodyComp(@Body request: BodyCompRequest): AccountResponse

    @PATCH(ACCOUNT + WEIGHTLESS)
    suspend fun updateWeightless(@Body request: WeightlessRequest): AccountResponse

    @PATCH(ACCOUNT + NOTIFICATION)
    suspend fun updateNotificationSettings(@Body request: NotificationSettingsRequest): AccountResponse

    @PATCH(ACCOUNT + STREAK)
    suspend fun updateStreak(@Body request: StreakRequest): AccountResponse

    @PATCH(ACCOUNT + DASHBOARD_TYPE)
    suspend fun updateDashboardType(@Body request: DashboardTypeRequest): AccountResponse

    @PATCH(ACCOUNT + DASHBOARD_METRICS)
    suspend fun updateDashboardMetrics(@Body request: DashboardMetricsRequest): AccountResponse

    @DELETE(ACCOUNT)
    suspend fun deleteAccount(): Unit
}
