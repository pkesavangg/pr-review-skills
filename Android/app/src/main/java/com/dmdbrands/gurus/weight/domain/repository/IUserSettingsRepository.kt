package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

/**
 * Repository interface for user settings operations.
 * Handles streak and weightless mode settings.
 */
interface IUserSettingsRepository {
    /**
     * Updates the streak setting for the active account.
     * @param streakRequest The streak setting to update
     * @return Updated account with new streak settings
     * API + DB
     */
    suspend fun updateStreakSetting(streakRequest: StreakRequest): Unit

    /**
     * Updates the weightless setting for the active account.
     * @param weightlessRequest The weightless setting to update
     * @return Updated account with new weightless settings
     * API + DB
     */
    suspend fun updateWeightlessSetting(weightlessRequest: WeightlessRequest)

    /**
     * Updates streak setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The streak setting request
     * @return Updated account with new streak settings
     * DB alone
     */
    suspend fun updateStreakSettingOffline(request: StreakRequest): Account?

    /**
     * Updates weightless setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The weightless setting request
     * @return Updated account with new weightless settings
     */
    suspend fun updateWeightlessSettingOffline(request: WeightlessRequest): Account?

    /**
     * Gets the active account if it has unsynced weightless settings.
     * Used by offline handler service to sync pending weightless changes for active account.
     * @return The active account with unsynced weightless settings, or null if active account is synced
     */
    suspend fun getUnsyncedActiveWeightlessAccountFromDB(): Account?

    /**
     * Gets the active account if it has unsynced streak settings.
     * Used by offline handler service to sync pending streak changes for active account.
     * @return The active account with unsynced streak settings, or null if active account is synced
     */
    suspend fun getUnsyncedActiveStreakAccountFromDB(): Account?
}
