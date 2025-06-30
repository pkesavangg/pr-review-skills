package com.greatergoods.meapp.domain.model.api.user

/**
 * Request model for updating body composition settings.
 * Corresponds to the BodyComp interface in Angular.
 *
 * @property height User's height in centimeters
 * @property activityLevel User's activity level ("normal" or "athlete")
 * @property weightUnit User's preferred weight unit (LB or KG)
 */
data class BodyCompUpdateRequest(
    val height: Int,
    val activityLevel: String,
    val weightUnit: String
)
