package com.greatergoods.meapp.features.common.helper.form

import android.util.Patterns
import java.util.Calendar

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
    fun required(): (formField: FormField<Any>) -> String? = { formField ->
        when (val value = formField.value) {
            is String -> {
                if (value.toString().isEmpty()) ValidationType.REQUIRED else null
            }
            is Boolean -> {
                if (value == false) ValidationType.REQUIRED else null
            }
            else -> {
                null
            }
        }
    }

    fun minLength(length: Int): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value
        if (value.toString().length < length) {
            ValidationType.MIN_LENGTH
        } else {
            null
        }
    }

    fun maxLength(length: Int): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value
        if (value.toString().length > length) {
            ValidationType.MAX_LENGTH
        } else {
            null
        }
    }

    fun email(): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value
        if (!Patterns.EMAIL_ADDRESS.matcher(value.toString()).matches()) {
            ValidationType.EMAIL
        } else {
            null
        }
    }

    fun pattern(pattern: String): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value
        if (!pattern.toRegex().matches(value.toString())) {
            ValidationType.PATTERN
        } else {
            null
        }
    }

    fun matchPassword(fieldName: String): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value
        val form = formField.parent
        if (value.toString().isNotEmpty() && (form != null && form.getValue<Any>(fieldName) != value)) {
            ValidationType.MATCH_PASSWORD
        } else {
            null
        }
    }

    fun notSame(fieldName: String): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value
        val form = formField.parent
        if (value.toString().isNotEmpty() && (form != null && form.getValue<Any>(fieldName) == value)) {
            ValidationType.NOT_SAME
        } else {
            null
        }
    }

    fun range(range: IntRange): (formField: FormField<Any>) -> String? = { formField ->
        try {
            val value = formField.value.toString().toInt()
            if (value !in range) {
                ValidationType.NOT_IN_RANGE
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            ValidationType.NOT_IN_RANGE
        }
    }

    fun greaterThan(fieldName: String): (formField: FormField<Any>) -> String? = { formField ->
        val value: Int = formField.value.toString().toIntOrNull() ?: 0
        val form = formField.parent
        if (value.toString().isNotEmpty() && form != null) {
            val comparingValue: Int = form.getValue<Int>(fieldName).toString().toIntOrNull() ?: 0
            if (value < comparingValue)
                ValidationType.LESSER
            else
                null
        } else
            null
    }

    fun lesserThan(fieldName: String): (formField: FormField<Any>) -> String? = { formField ->
        val value: Int = formField.value.toString().toIntOrNull() ?: 0
        val form = formField.parent
        if (value.toString().isNotEmpty() && form != null) {
            val comparingValue: Int = form.getValue<Int>(fieldName).toString().toIntOrNull() ?: 0
            if (value > comparingValue)
                ValidationType.GREATER
            else
                null
        } else
            null
    }

    fun futureTime(): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value as Calendar
        val currTime = Calendar.getInstance() // Replace with CalendarUtil.getCurrentDate() if available
        if (value.timeInMillis > currTime.timeInMillis) {
            ValidationType.FUTURE_TIME
        } else {
            null
        }
    }

    fun skuValidator(): (formField: FormField<Any>) -> String? = { formField ->
        val value = formField.value.toString()
        if (value.length != 4 || !value.all { it.isDigit() }) ValidationType.PATTERN else null
    }

    fun weightValidator(unitType: String): (formField: FormField<Any>) -> String? = { formField ->
        val valueString = formField.value.toString()
        
        // If empty, return required error
        if (valueString.isBlank()) {
            ValidationType.REQUIRED
        } else {
            // Insert decimal point at the correct position (1 digit from right)
            val decimalValue = if (valueString.length > 1) {
                valueString.dropLast(1) + "." + valueString.takeLast(1)
            } else {
                "0." + valueString
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
                } else { // lbs
                    when {
                        v <= 0f -> ValidationType.NOT_IN_RANGE
                        v > 999f -> ValidationType.NOT_IN_RANGE
                        else -> null
                    }
                }
            }
        }
    }

    fun bodyCompValidator(min: Int = 0, max: Int = 99, allowDecimal: Boolean = true): (formField: FormField<Any>) -> String? = { formField ->
        val valueString = formField.value.toString()
        
        // If empty, return required error
        if (valueString.isBlank()) {
            ValidationType.REQUIRED
        } else {
            // Handle decimal transformation if allowed
            val decimalValue = if (allowDecimal) {
                if (valueString.length > 1) {
                    valueString.dropLast(1) + "." + valueString.takeLast(1)
                } else {
                    "0." + valueString
                }
            } else {
                valueString
            }

            val v = if (allowDecimal) {
                decimalValue.toFloatOrNull()
            } else {
                valueString.toIntOrNull()?.toFloat()
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
}
