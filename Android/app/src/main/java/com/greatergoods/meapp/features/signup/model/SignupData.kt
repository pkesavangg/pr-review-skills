package com.greatergoods.meapp.features.signup.model

import com.greatergoods.meapp.features.common.components.HeightInput

/**
 * Represents gender selection in signup
 */
enum class Gender {
    MALE,
    FEMALE
}

/**
 * Represents goal type for weight management
 */
enum class GoalType {
    MAINTAIN,
    LOSE_GAIN
}

/**
 * Data class representing all signup information
 */
data class SignupData(
    val firstName: String = "",
    val lastName: String = "",
    val birthday: Long? = null,
    val gender: Gender? = null,
    val height: HeightInput = HeightInput.FtIn(0, 0),
    val goalType: GoalType = GoalType.LOSE_GAIN,
    val currentWeight: Float? = null,
    val goalWeight: Float? = null,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val zipcode: String = ""
)

/**
 * Step definitions for the signup process
 */
enum class SignupStep {
    NAME,
    BIRTHDAY,
    GENDER,
    HEIGHT,
    GOAL,
    EMAIL,
    PASSWORD
}
