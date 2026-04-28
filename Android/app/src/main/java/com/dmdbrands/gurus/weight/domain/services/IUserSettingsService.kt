package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.coroutines.flow.StateFlow

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
     * Current default graph segment as a StateFlow.
     * Use [StateFlow.value] for synchronous reads in init blocks.
     * Collect as a Flow for reactive updates.
     */
    val defaultGraphSegment: StateFlow<GraphSegment>

    /**
     * Persists the user's preferred default graph segment (device-local, no backend sync).
     */
    suspend fun setDefaultGraphSegment(segment: GraphSegment)
}
