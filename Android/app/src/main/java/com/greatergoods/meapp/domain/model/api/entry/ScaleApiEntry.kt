package com.greatergoods.meapp.domain.model.api.entry

data class ScaleApiEntry(
    val operationType: String,
    val entryTimestamp: String,
    val serverTimestamp: String?,
    val weight: Int,
    val bodyFat: Double?,
    val muscleMass: Double?,
    val boneMass: Double?,
    val water: Double?,
    val bmi: Double?,
    val source: String?,
    val unit: String? = null,
    val impedance: Int? = null,
    val pulse: Int? = null,
    val visceralFatLevel: Double? = null,
    val subcutaneousFatPercent: Double? = null,
    val proteinPercent: Double? = null,
    val skeletalMusclePercent: Double? = null,
    val bmr: Double? = null,
    val metabolicAge: Int? = null,
)
