package com.greatergoods.meapp.features.signup.model

import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.HeightInput
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId.systemDefault

/**
 * Represents gender selection in signup
 */
enum class Gender {
    MALE,
    FEMALE,
}

/**
 * Represents goal type for weight management
 */
enum class GoalType {
    MAINTAIN,
    LOSE_GAIN,
}

/**
 * Data class representing all signup information
 */
@Serializable
data class SignupData(
    val firstName: String = "",
    val lastName: String = "",
    val birthday: DateTimeValue =
        DateTimeValue.Date(
            LocalDate
                .parse("2000-01-01")
                .atStartOfDay(systemDefault())
                .toInstant()
                .toEpochMilli(),
        ),
    val gender: String = "",
    val height: HeightInput = HeightInput.FtIn(5, 10),
    val goalType: String = "",
    val currentWeight: String = "",
    val goalWeight: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val zipcode: String = "",
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
    PASSWORD,
}
