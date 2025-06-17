package com.greatergoods.meapp.domain.model.storage.entry

data class PeriodBodyScaleSummary(
    val period: String, // "YYYY-MM" for month, "YYYY-MM-DD" for day
    val entryTimestamp: Long, // For average: latest timestamp in period; for latest: timestamp of the latest entry
    val weight: Double,
    val bodyFat: Double?,
    val muscleMass: Double?,
    val water: Double?,
    val bmi: Double?,
    val bmr: Double?,
    val metabolicAge: Double?,
    val proteinPercent: Double?,
    val pulse: Double?,
    val skeletalMusclePercent: Double?,
    val subcutaneousFatPercent: Double?,
    val visceralFatLevel: Double?,
    val boneMass: Double?,
    val impedance: Double?,
    val unit: String?
)
