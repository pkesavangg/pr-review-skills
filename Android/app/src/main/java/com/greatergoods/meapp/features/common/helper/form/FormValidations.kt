package com.greatergoods.meapp.features.common.helper.form

import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.features.signup.model.SignupFormControls
import java.util.Calendar

object ValidationType {
    const val MATCH_PASSWORD = "matchPassword"
    const val NOT_SAME = "notSame"
    const val REQUIRED = "required"
    const val EMAIL = "Invalid email"
    const val MIN_LENGTH = "Invalid"
    const val MAX_LENGTH = "max_length"
    const val PATTERN = "pattern"
    const val NOT_IN_RANGE = "not_in_range"
    const val GREATER = "greater"
    const val LESSER = "lesser"
    const val FUTURE_TIME = "future_time"
}

object ValidationMessages {
    const val RANGE = "value must be between %d and %d"
    const val INVALID_NUMBER = "invalid number"
    const val INVALID_EMAIL = "must use a valid email"
    const val PATTERN = "invalid"
    const val NOT_SAME = "value should not be same as other field"
    const val GREATER_THAN = "value must be greater than %s"
    const val LESS_THAN = "value must be less than %s"
    const val FUTURE_TIME = "date must not be in the future"
    const val SKU = "model number invalid"
    const val REQUIRED = "must not leave blank"
    const val PASSWORD_MISMATCH = "passwords mismatch"
    const val NO_WHITESPACE = "must not leave blank"
    const val INVALID_WEIGHT = "invalid weight"
    const val KG_RANGE = "weight must be between 0kg and 450 kg"
    const val LB_RANGE = "weight must be between 0lbs and 999 lbs"
}

object FormValidations {
    fun required(): Validator<String> =
        { value ->
            if (value.isBlank()) {
                ValidationError(ValidationType.REQUIRED, ValidationMessages.REQUIRED)
            } else {
                null
            }
        }

    fun minLength(
        length: Int,
        fieldName: String = "Field",
    ): Validator<String> =
        { value ->
            if (value.length < length) {
                ValidationError(ValidationType.MIN_LENGTH, "$fieldName must be at least $length characters long")
            } else {
                null
            }
        }

    fun maxLength(
        length: Int,
        fieldName: String = "Field",
    ): Validator<String> =
        { value ->
            if (value.length > length) {
                ValidationError(ValidationType.MAX_LENGTH, "Maximum value should be $length")
            } else {
                null
            }
        }

    fun email(): Validator<String> =
        { value ->
            if (!AppValidatorConfig.Email.PATTERN.matches(value)) {
                ValidationError(ValidationType.EMAIL, ValidationMessages.INVALID_EMAIL)
            } else {
                null
            }
        }

    fun pattern(pattern: String): Validator<String> =
        { value ->
            if (!pattern.toRegex().matches(value)) {
                ValidationError(ValidationType.PATTERN, ValidationMessages.PATTERN)
            } else {
                null
            }
        }

    fun notSame(other: FormControl<String>): Validator<String> =
        { value ->
            if (value.isNotEmpty() && value == other.value) {
                ValidationError(ValidationType.NOT_SAME, ValidationMessages.NOT_SAME)
            } else {
                null
            }
        }

    fun range(range: IntRange): Validator<String> =
        { value ->
            try {
                val intValue = value.toInt()
                if (intValue !in range) {
                    ValidationError(
                        ValidationType.NOT_IN_RANGE,
                        String.format(ValidationMessages.RANGE, range.first, range.last),
                    )
                } else {
                    null
                }
            } catch (e: NumberFormatException) {
                ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
            }
        }

    fun greaterThan(other: FormControl<String>): Validator<String> =
        { value ->
            val intValue = value.toIntOrNull()
            val otherValue = other.value.toIntOrNull()
            if (value.isNotEmpty() && intValue != null && otherValue != null && intValue < otherValue) {
                ValidationError(ValidationType.LESSER, String.format(ValidationMessages.GREATER_THAN, other.value))
            } else {
                null
            }
        }

    fun lesserThan(other: FormControl<String>): Validator<String> =
        { value ->
            val intValue = value.toIntOrNull()
            val otherValue = other.value.toIntOrNull()
            if (value.isNotEmpty() && intValue != null && otherValue != null && intValue > otherValue) {
                ValidationError(ValidationType.GREATER, String.format(ValidationMessages.LESS_THAN, other.value))
            } else {
                null
            }
        }

    fun futureTime(): Validator<Calendar> =
        { value ->
            val currTime = Calendar.getInstance()
            if (value.timeInMillis > currTime.timeInMillis) {
                ValidationError(ValidationType.FUTURE_TIME, ValidationMessages.FUTURE_TIME)
            } else {
                null
            }
        }

    fun skuValidator(): Validator<String> =
        { value ->
            if (!AppValidatorConfig.SKU.PATTERN.matches(value)) {
                ValidationError(ValidationType.PATTERN, ValidationMessages.SKU)
            } else {
                null
            }
        }

    fun weightValidator(unitType: WeightUnit = WeightUnit.LB): Validator<String> =
        { value ->
            if (value.isNotBlank()) {
                val decimalValue =
                    if (value.length > 1) {
                        value.dropLast(1) + "." + value.takeLast(1)
                    } else {
                        "0." + value
                    }
                val v = decimalValue.toFloatOrNull()
                if (v == null) {
                    ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_WEIGHT)
                } else {
                    if (unitType == WeightUnit.KG) {
                        when {
                            v <= AppValidatorConfig.WeightKg.MIN || v > AppValidatorConfig.WeightKg.MAX ->
                                ValidationError(
                                    ValidationType.NOT_IN_RANGE,
                                    ValidationMessages.KG_RANGE,
                                )

                            else -> null
                        }
                    } else {
                        when {
                            v <= AppValidatorConfig.WeightLb.MIN || v > AppValidatorConfig.WeightLb.MAX ->
                                ValidationError(
                                    ValidationType.NOT_IN_RANGE,
                                    ValidationMessages.LB_RANGE,
                                )

                            else -> null
                        }
                    }
                }
            } else {
                null
            }
        }

    fun bodyCompValidator(
        min: Int = AppValidatorConfig.BodyComp.MIN,
        max: Int = AppValidatorConfig.BodyComp.MAX,
        allowDecimal: Boolean = true,
    ): Validator<String> =
        { value ->
            if (value.isNotBlank()) {
                val decimalValue =
                    if (allowDecimal) {
                        if (value.length > 1) value.dropLast(1) + "." + value.takeLast(1) else "0." + value
                    } else {
                        value
                    }

                val v =
                    if (allowDecimal) {
                        decimalValue.toFloatOrNull()
                    } else {
                        value.toIntOrNull()?.toFloat()
                    }

                if (v == null) {
                    ValidationError(ValidationType.NOT_IN_RANGE, ValidationMessages.INVALID_NUMBER)
                } else {
                    when {
                        v < min || v > max ->
                            ValidationError(
                                ValidationType.NOT_IN_RANGE,
                                String.format(ValidationMessages.RANGE, min, max),
                            )

                        else -> null
                    }
                }
            } else {
                null
            }
        }

    /**
     * This validator is specifically for confirm password field to check against password field
     */
    fun confirmPasswordMatch(formGroup: () -> FormGroup<SignupFormControls>): Validator<String> =
        { confirmPasswordValue ->
            val form = formGroup()
            val passwordValue = form.controls.password.value

            // Only show mismatch error if both fields have values and they don't match
            if (confirmPasswordValue.isNotEmpty() &&
                passwordValue.isNotEmpty() &&
                confirmPasswordValue != passwordValue
            ) {
                ValidationError(ValidationType.MATCH_PASSWORD, ValidationMessages.PASSWORD_MISMATCH)
            } else {
                null
            }
        }

    fun noWhitespace(): Validator<String> =
        { value ->
            if (value.trim().isEmpty()) {
                ValidationError(ValidationType.REQUIRED, ValidationMessages.NO_WHITESPACE)
            } else {
                null
            }
        }
}
