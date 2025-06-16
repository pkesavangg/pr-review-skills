package com.greatergoods.meapp.features.common.helper.form

import android.util.Patterns
import java.util.Calendar
import com.greatergoods.meapp.features.common.helper.form.FormControl

object ValidationType {
    const val MATCH_PASSWORD = "matchPassword"
    const val NOT_SAME = "notSame"
    const val REQUIRED = "required"
    const val EMAIL = "email"
    const val MIN_LENGTH = "min_length"
    const val MAX_LENGTH = "max_length"
    const val PATTERN = "pattern"
    const val NOT_IN_RANGE = "not_in_range"
    const val GREATER = "greater"
    const val LESSER = "lesser"
    const val FUTURE_TIME = "future_time"
}

object FormValidations {
    fun required(): Validator<String> = { value ->
        if (value.isEmpty()) ValidationType.REQUIRED else null
    }

    fun minLength(length: Int): Validator<String> = { value ->
        if (value.length < length) ValidationType.MIN_LENGTH else null
    }

    fun maxLength(length: Int): Validator<String> = { value ->
        if (value.length > length) ValidationType.MAX_LENGTH else null
    }

    fun email(): Validator<String> = { value ->
        if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) ValidationType.EMAIL else null
    }

    fun pattern(pattern: String): Validator<String> = { value ->
        if (!pattern.toRegex().matches(value)) ValidationType.PATTERN else null
    }

    fun notSame(other: FormControl<String>): Validator<String> = { value ->
        if (value.isNotEmpty() && value == other.value) ValidationType.NOT_SAME else null
    }

    fun range(range: IntRange): Validator<String> = { value ->
        try {
            val intValue = value.toInt()
            if (intValue !in range) ValidationType.NOT_IN_RANGE else null
        } catch (e: NumberFormatException) {
            ValidationType.NOT_IN_RANGE
        }
    }

    fun greaterThan(other: FormControl<String>): Validator<String> = { value ->
        val intValue = value.toIntOrNull() ?: 0
        val otherValue = other.value.toIntOrNull() ?: 0
        if (value.isNotEmpty() && intValue < otherValue) ValidationType.LESSER else null
    }

    fun lesserThan(other: FormControl<String>): Validator<String> = { value ->
        val intValue = value.toIntOrNull() ?: 0
        val otherValue = other.value.toIntOrNull() ?: 0
        if (value.isNotEmpty() && intValue > otherValue) ValidationType.GREATER else null
    }

    fun futureTime(): Validator<Calendar> = { value ->
        val currTime = Calendar.getInstance()
        if (value.timeInMillis > currTime.timeInMillis) ValidationType.FUTURE_TIME else null
    }

    fun skuValidator(): Validator<String> = { value ->
        if (value.length != 4 || !value.all { it.isDigit() }) ValidationType.PATTERN else null
    }

    fun weightValidator(unitType: String): Validator<String> = { value ->
        if (value.isBlank()) {
            ValidationType.REQUIRED
        } else {
            val decimalValue = if (value.length > 1) {
                value.dropLast(1) + "." + value.takeLast(1)
            } else {
                "0." + value
            }
            val v = decimalValue.toFloatOrNull()
            if (v == null) {
                ValidationType.NOT_IN_RANGE
            } else {
                if (unitType == "kg") {
                    when {
                        v <= 0f -> ValidationType.NOT_IN_RANGE
                        v > 450f -> ValidationType.NOT_IN_RANGE
                        else -> null
                    }
                } else {
                    when {
                        v <= 0f -> ValidationType.NOT_IN_RANGE
                        v > 999f -> ValidationType.NOT_IN_RANGE
                        else -> null
                    }
                }
            }
        }
    }

    fun bodyCompValidator(min: Int = 0, max: Int = 99, allowDecimal: Boolean = true): Validator<String> = { value ->
        if (value.isBlank()) {
            ValidationType.REQUIRED
        } else {
            val decimalValue = if (allowDecimal) {
                if (value.length > 1) {
                    value.dropLast(1) + "." + value.takeLast(1)
                } else {
                    "0." + value
                }
            } else {
                value
            }
            val v = if (allowDecimal) {
                decimalValue.toFloatOrNull()
            } else {
                value.toIntOrNull()?.toFloat()
            }
            if (v == null) {
                ValidationType.NOT_IN_RANGE
            } else {
                when {
                    v < min -> ValidationType.NOT_IN_RANGE
                    v > max -> ValidationType.NOT_IN_RANGE
                    else -> null
                }
            }
        }
    }

    /**
     * Validator for confirm password fields. Checks if the value matches the value of the provided password FormControl.
     * Returns 'Passwords mismatch' if they do not match.
     */
    fun confirmPasswordValidator(passwordControl: FormControl<String>): Validator<String> = { value ->
        if (value != passwordControl.value) {
            "Passwords mismatch"
        } else {
            null
        }
    }
}
