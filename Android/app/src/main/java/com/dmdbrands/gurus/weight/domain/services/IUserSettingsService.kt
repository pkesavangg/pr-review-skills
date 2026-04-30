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
     * Eagerly-started [StateFlow] of the persisted default graph segment.
     * `.value` is safe to read synchronously — the initial seed is [GraphSegment.DEFAULT]
     * and is replaced as soon as DataStore emits the persisted value (or stays at
     * [GraphSegment.DEFAULT] on upstream error via `.catch` inside the implementation).
     */
    val defaultGraphSegment: StateFlow<GraphSegment>

    /**
     * Persists the user's preferred default graph segment (device-local, no backend sync).
     */
    suspend fun setDefaultGraphSegment(segment: GraphSegment)
}
