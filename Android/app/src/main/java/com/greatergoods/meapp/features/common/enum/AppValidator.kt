package com.greatergoods.meapp.features.common.enum

/**
 * Enum containing validation constraints for form fields.
 * Includes maxLength, minLength, min, max values and regex patterns where needed.
 */
enum class AppValidator {
    // Email validation
    EMAIL {
        override val maxLength: Int = 100
        override val pattern: String = android.util.Patterns.EMAIL_ADDRESS.toString()
        override val minLength: Int = 1
    },

    // Password validation
    PASSWORD {
        override val maxLength: Int = 50
        override val minLength: Int = 6
    },

    // Name validation (first name, last name)
    NAME {
        override val maxLength: Int = 100
        override val minLength: Int = 1
    },

    // Zipcode validation
    ZIPCODE {
        override val maxLength: Int = 20
        override val minLength: Int = 1
    },

    // Date of birth validation
    DATE_OF_BIRTH {
        override val maxLength: Int = 10
        override val minLength: Int = 10
    },

    // BMR (Basal Metabolic Rate) validation
    BMR {
        override val maxLength: Int = 4
        override val minLength: Int = 1
        override val min: Int = 800
        override val max: Int = 5000
    },

    // Metabolic Age validation
    METABOLIC_AGE {
        override val maxLength: Int = 3
        override val minLength: Int = 1
        override val min: Int = 12
        override val max: Int = 100
    },

    // Body Composition validation (percentage)
    BODY_COMP {
        override val maxLength: Int = 5
        override val minLength: Int = 1
        override val min: Int = 0
        override val max: Int = 100
    },

    // Weight validation (kg)
    WEIGHT_KG {
        override val maxLength: Int = 5
        override val minLength: Int = 1
        override val min: Int = 0
        override val max: Int = 450
    },

    // Weight validation (lb)
    WEIGHT_LB {
        override val maxLength: Int = 5
        override val minLength: Int = 1
        override val min: Int = 0
        override val max: Int = 1000
    },

    // Height validation (mm)
    HEIGHT_MM {
        override val maxLength: Int = 4
        override val minLength: Int = 3
        override val min: Int = 500
        override val max: Int = 3000
    },

    // SKU validation (4-digit numeric)
    SKU {
        override val maxLength: Int = 4
        override val minLength: Int = 4
        override val pattern: String = "^[0-9]{4}$"
    };

    /**
     * Maximum length constraint for the field.
     */
    open val maxLength: Int = 255

    /**
     * Minimum length constraint for the field.
     */
    open val minLength: Int = 0

    /**
     * Minimum value constraint for the field.
     */
    open val min: Int = 0

    /**
     * Maximum value constraint for the field.
     */
    open val max: Int = 50

    /**
     * Regex pattern for validation.
     */
    open val pattern: String = ".*"

    /**
     * Validates if the given value matches the pattern.
     */
    fun matches(value: String): Boolean = pattern.toRegex().matches(value)
}
