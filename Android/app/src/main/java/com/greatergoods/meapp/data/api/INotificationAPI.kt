package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import retrofit2.http.Body
import retrofit2.http.PATCH
import javax.inject.Singleton

/**
 * API interface for notification endpoints.
 */
@Singleton
interface INotificationAPI {
    companion object {
        private const val NOTIFICATION = "account/notification"
    }

    /**
     * Updates notification settings for the current user.
     *
     * @param request The notification settings to update
     * @return AccountResponse with updated account data
     */
    @PATCH(NOTIFICATION)
    suspend fun updateNotificationSettings(@Body request: NotificationSettingsRequest): AccountResponse
}
