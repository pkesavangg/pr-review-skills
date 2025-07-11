package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest

/**
 * Body composition update types for local database updates.
 */
enum class BodyCompUpdateType {
    ACTIVITY_LEVEL,
    WEIGHT_UNIT,
    HEIGHT
}

/**
 * Service interface for managing body composition settings.
 * Handles activity level, weight unit, and height updates with offline support.
 */
interface IBodyCompositionService {

    /**
     * Updates body composition data in the local database.
     * This method handles offline updates for any body composition field.
     *
     * @param updateType The type of update (activity level, weight unit, or height)
     * @param bodyComposition The new value (String for activity level, WeightUnit for weight unit, Int for height)
     * @return The updated account or null if update fails
     */
    suspend fun updateBodyComposition(updateType: BodyCompUpdateType, bodyComposition: BodyCompUpdateRequest): Unit
}
