package com.dmdbrands.gurus.weight.domain.enums

/**
 * Constants for metric keys in camelCase format.
 * These are the standardized keys used for database and server communication.
 */
object MetricKeyConstants {
    // Core metrics
    const val WEIGHT = "weight"
    const val BMI = "bmi"
    const val BODY_FAT = "bodyFat"
    const val MUSCLE_MASS = "muscleMass"
    const val BODY_WATER = "water"
    const val HEART_RATE = "pulse"

    // Advanced metrics
    const val BONE_MASS = "boneMass"
    const val VISCERAL_FAT = "visceralFatLevel"
    const val SUBCUTANEOUS_FAT = "subcutaneousFatPercent"
    const val PROTEIN = "proteinPercent"
    const val SKELETAL_MUSCLE = "skeletalMusclePercent"
    const val BMR = "bmr"
    const val METABOLIC_AGE = "metabolicAge"

    /**
     * All metric keys in camelCase format.
     * Matches the order specified by the user.
     */
    val ALL_METRIC_KEYS = listOf(
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
        METABOLIC_AGE,
    )

    /**
     * Default 4-metric dashboard keys in camelCase format.
     */
    val DEFAULT_4_METRICS = listOf(
        BMI,
        BODY_FAT,
        MUSCLE_MASS,
        BODY_WATER
    )

    /**
     * Maps camelCase keys to MetricKey enums.
     */
    val CAMEL_CASE_TO_ENUM = mapOf(
        WEIGHT to MetricKey.WEIGHT,
        BMI to MetricKey.BMI,
        BODY_FAT to MetricKey.BODY_FAT,
        MUSCLE_MASS to MetricKey.MUSCLE_MASS,
        BODY_WATER to MetricKey.BODY_WATER,
        HEART_RATE to MetricKey.HEART_RATE,
        BONE_MASS to MetricKey.BONE_MASS,
        VISCERAL_FAT to MetricKey.VISCERAL_FAT,
        SUBCUTANEOUS_FAT to MetricKey.SUBCUTANEOUS_FAT,
        PROTEIN to MetricKey.PROTEIN,
        SKELETAL_MUSCLE to MetricKey.SKELETAL_MUSCLE,
        BMR to MetricKey.BMR,
        METABOLIC_AGE to MetricKey.METABOLIC_AGE
    )

    /**
     * Maps MetricKey enums to camelCase keys.
     */
    val ENUM_TO_CAMEL_CASE = CAMEL_CASE_TO_ENUM.entries.associate { (key, value) -> value to key }
}
