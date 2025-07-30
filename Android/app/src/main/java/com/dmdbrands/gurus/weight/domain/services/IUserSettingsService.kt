package com.dmdbrands.gurus.weight.domain.services

/**
 * Service interface for user settings operations.
 * Handles business logic for streak and weightless mode settings.
 */
interface IUserSettingsService {

    /**
     * Toggles the streak setting for the active account.
     * @param isStreakOn Boolean indicating if streak should be enabled
     * @return Updated account with new streak settings
     */
    suspend fun toggleStreakSetting(isStreakOn: Boolean)

    /**
     * Toggles the weightless setting for the active account.
     * @param isWeightlessOn Boolean indicating if weightless mode should be enabled
     * @param weightlessWeight Weight to use when weightless mode is enabled (optional)
     * @return Updated account with new weightless settings
     */
    suspend fun toggleWeightlessSetting(
        isWeightlessOn: Boolean,
        weightlessWeight: Double? = null
    )
}
