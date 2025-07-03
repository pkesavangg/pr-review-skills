package com.greatergoods.meapp.features.signup.model

import com.greatergoods.meapp.domain.enums.GoalType
import com.greatergoods.meapp.features.common.components.DateTimeValue
import com.greatergoods.meapp.features.common.components.HeightInput
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId.systemDefault

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

/**Add commentMore actions
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
    val sex: String = "",
    val height: HeightInput = HeightInput.FtIn(5, 10),
    val goalType: String = GoalType.LOSE_GAIN.value,
    val currentWeight: String = "",
    val goalWeight: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val zipcode: String = "",
    val unitMetric: Boolean = false,
)
