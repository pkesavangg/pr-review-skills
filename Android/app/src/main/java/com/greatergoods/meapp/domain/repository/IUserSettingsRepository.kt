package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.WeightlessSettingsEntity
import com.greatergoods.meapp.domain.model.api.metrics.StreakRequest
import com.greatergoods.meapp.domain.model.api.metrics.WeightlessRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * Repository interface for user settings operations.
 * Handles streak and weightless mode settings.
 */
interface IUserSettingsRepository {
    suspend fun updateWeightless(weightlessRequest: WeightlessSettingsEntity)

    /**
     * Updates the streak setting for the active account.
     * @param streakRequest The streak setting to update
     * @return Updated account with new streak settings
     */
    suspend fun updateStreakSetting(streakRequest: StreakRequest): Account?

    /**
     * Updates the weightless setting for the active account.
     * @param weightlessRequest The weightless setting to update
     * @return Updated account with new weightless settings
     */
    suspend fun updateWeightlessSetting(weightlessRequest: WeightlessRequest): Account?

    /**
     * Updates streak setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The streak setting request
     * @return Updated account with new streak settings
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
     * Gets accounts with unsynced streak settings changes.
     * Used by offline handler service for syncing streak settings specifically.
     * @return List of accounts with pending streak settings changes
     */
    suspend fun getUnsyncedStreakAccountsFromDB(): List<Account>

    /**
     * Gets accounts with unsynced weightless settings changes.
     * Used by offline handler service for syncing weightless settings specifically.
     * @return List of accounts with pending weightless settings changes
     */
    suspend fun getUnsyncedWeightlessAccountsFromDB(): List<Account>
}
