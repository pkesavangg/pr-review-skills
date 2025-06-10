package com.greatergoods.meapp.features.common.components.input

/**
 * Validators for various input types.
 */

fun skuValidator(value: String): String? =
    if (value.length != 4 || !value.all { it.isDigit() }) "SKU must be 4 digits" else null

fun weightValidator(value: String, isMetric: Boolean): String? {
    val v = value.toFloatOrNull() ?: return "Value should be greater than 0"
    return if (isMetric) {
        when {
            v <= 0 -> "Value should be greater than 0 kg"
            v >= 450 -> "Value should be less than 450kg"
            else -> null
        }
    } else {
        when {
            v <= 0 -> "Value should be greater than 0 lbs"
            v >= 999 -> "Value should be less than 999 lbs"
            else -> null
        }
    }
}

fun bodyCompValidator(value: String, min: Int = 0, max: Int = 99): String? {
    val v = value.toIntOrNull() ?: return null
    return when {
        v <= min -> "Value should be greater than $min"
        v >= max -> "Value should be less than $max"
        else -> null
    }
} 