package com.dmdbrands.gurus.weight.domain.enums

/**
 * Enum representing different metric keys for dashboard metrics.
 * Replaces the proto MetricKey definition.
 */
enum class MetricKey {
    WEIGHT,
    BMI,
    BODY_FAT,
    MUSCLE_MASS,
    BODY_WATER,
    HEART_RATE,
    BONE_MASS,
    VISCERAL_FAT,
    SUBCUTANEOUS_FAT,
    PROTEIN,
    SKELETAL_MUSCLE,
    BMR,
    METABOLIC_AGE;

    /**
     * Converts string to MetricKey enum.
     * @param value The string value to convert
     * @return The corresponding MetricKey or null if not found
     */
    companion object {
        fun fromString(value: String): MetricKey? {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /**
         * Gets the default metric keys for 4-metric dashboard.
         */
        fun getDefault4Metrics(): List<MetricKey> = listOf(
            BMI,
            BODY_FAT,
            MUSCLE_MASS,
            BODY_WATER
        )

        /**
         * Gets all available metric keys for 12-metric dashboard.
         */
        fun getAllMetrics(): List<MetricKey> = listOf(
            BMI,
            BODY_FAT,
            MUSCLE_MASS,
            BODY_WATER,
            HEART_RATE,
            BONE_MASS,
            VISCERAL_FAT,
            SUBCUTANEOUS_FAT,
            PROTEIN,
            SKELETAL_MUSCLE,
            BMR,
            METABOLIC_AGE
        )
    }
}
