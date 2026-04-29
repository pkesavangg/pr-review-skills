package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for user settings operations.
 * Handles business logic for streak, weightless mode, and display preference settings.
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

    /**
     * Cold [Flow] of the persisted default graph segment for the active account.
     * The first emission is the actual persisted value (or [GraphSegment.MONTH] for fresh
     * installs / accounts that never set a default); subsequent emissions react to in-app
     * changes. On upstream errors the flow falls back to [GraphSegment.MONTH] via
     * [kotlinx.coroutines.flow.catch] inside the implementation.
     */
    val defaultGraphSegment: Flow<GraphSegment>

    /**
     * Persists the user's preferred default graph segment (device-local, no backend sync).
     */
    suspend fun setDefaultGraphSegment(segment: GraphSegment)
}
