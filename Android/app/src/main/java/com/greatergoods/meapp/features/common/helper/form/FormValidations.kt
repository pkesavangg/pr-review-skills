package com.greatergoods.meapp.features.common.helper.form

import java.util.Calendar
import android.util.Patterns

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
    const val RANGE = "Value must be between %d and %d"
    const val INVALID_NUMBER = "Invalid number"
    const val INVALID_EMAIL = "Must use a valid email"
    const val PATTERN = "Value does not match required pattern"
    const val NOT_SAME = "Value should not be same as other field"
    const val GREATER_THAN = "Value must be greater than %s"
    const val LESS_THAN = "Value must be less than %s"
    const val FUTURE_TIME = "Date must not be in the future"
    const val SKU = "SKU must be 4-digit numeric"
    const val REQUIRED = "Must not leave blank"
    const val PASSWORD_MISMATCH = "Passwords mismatch"
    const val NO_WHITESPACE = "Must not leave blank"
    const val INVALID_WEIGHT = "Invalid weight"
    const val KG_RANGE = "Weight must be between 0.1 and 450 kg"
    const val LB_RANGE = "Weight must be between 0.1 and 999 lb"
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
            if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
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
            if (value.length != 4 || !value.all { it.isDigit() }) {
                ValidationError(ValidationType.PATTERN, ValidationMessages.SKU)
            } else {
                null
            }
        }

    fun weightValidator(unitType: String): Validator<String> =
        { value ->
            if (value.isBlank()) {
                ValidationError(ValidationType.REQUIRED, ValidationMessages.INVALID_WEIGHT)
            } else {
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
                    if (unitType == "kg") {
                        when {
                            v <= 0f || v > 450f ->
                                ValidationError(
                                    ValidationType.NOT_IN_RANGE,
                                    ValidationMessages.KG_RANGE,
                                )

                            else -> null
                        }
                    } else {
                        when {
                            v <= 0f || v > 999f ->
                                ValidationError(
                                    ValidationType.NOT_IN_RANGE,
                                    ValidationMessages.LB_RANGE,
                                )

                            else -> null
                        }
                    }
                }
            }
        }

    fun bodyCompValidator(
        min: Int = 0,
        max: Int = 99,
        allowDecimal: Boolean = true,
    ): Validator<String> =
        { value ->
            if (value.isBlank()) {
                ValidationError(ValidationType.REQUIRED, ValidationMessages.REQUIRED)
            } else {
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
            }
        }

    fun confirmPasswordValidator(passwordControl: FormControl<String>): Validator<String> =
        { value ->
            if (value != passwordControl.value) {
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
