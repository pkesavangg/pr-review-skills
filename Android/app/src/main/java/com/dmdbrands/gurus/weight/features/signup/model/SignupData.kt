package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneOffset

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
        .parse(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE)
        .atStartOfDay(ZoneOffset.UTC) // use UTC midnight to avoid timezone shift in picker
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
  val useMetric: Boolean = false,
)
