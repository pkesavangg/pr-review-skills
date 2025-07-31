package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.proto.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for app-wide data operations such as theme and FCM token.
 */
interface IAppRepository {
    /**
     * Emits the current [ThemeMode] and updates on changes.
     */
    val themeModeFlow: Flow<ThemeMode>

    /**
     * Emits the current FCM token and updates on changes.
     */
    val fcmTokenFlow: Flow<String>

    /**
     * Gets the current [ThemeMode].
     */
    suspend fun getThemeMode(): ThemeMode

    /**
     * Sets the [ThemeMode].
     */
    suspend fun setThemeMode(accountId: String, mode: ThemeMode)

    /**
     * Clears the theme mode data.
     */
    suspend fun clearThemeMode()

    /**
     * Gets the current FCM token.
     */
    suspend fun getFcmToken(): String

    /**
     * Sets the FCM token.
     */
    suspend fun setFcmToken(token: String)

    /**
     * Clears the FCM token data.
     */
    suspend fun clearFcmToken()
}

