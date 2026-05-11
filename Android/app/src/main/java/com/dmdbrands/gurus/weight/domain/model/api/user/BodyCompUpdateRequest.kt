package com.dmdbrands.gurus.weight.domain.model.api.user

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
) {
    companion object {
        /** Fallback stored-height (tenths of inches) used when the account has no saved height. */
        const val DEFAULT_HEIGHT = 1700

        /** Fallback activity level used when the account has none set. */
        const val DEFAULT_ACTIVITY_LEVEL = "normal"
    }
}
