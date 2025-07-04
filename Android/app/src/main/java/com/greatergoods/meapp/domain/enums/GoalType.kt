package com.greatergoods.meapp.domain.enums

/**
 * Represents goal type for weight management
 *
 * @property value The string value used for API communication and storage
 */
enum class GoalType(
    val value: String,
) {
    /** Maintain current weight */
    MAINTAIN("maintain"),

    /** Lose or gain weight */
    LOSE_GAIN("losegain"),
    LOSE("lose"),
    GAIN("gain"),
}
