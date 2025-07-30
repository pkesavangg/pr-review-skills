package com.dmdbrands.gurus.weight.domain.model.api.entry

data class ScaleApiEntry(
  val operationType: String,
  val entryTimestamp: String,
  val serverTimestamp: String?,
  val weight: Int,
  val bodyFat: Int?,
  val muscleMass: Int?,
  val boneMass: Int?,
  val water: Int?,
  val bmi: Int?,
  val source: String?,
  val unit: String? = null,
  val impedance: Int? = null,
  val pulse: Int? = null,
  val visceralFatLevel: Int? = null,
  val subcutaneousFatPercent: Int? = null,
  val proteinPercent: Int? = null,
  val skeletalMusclePercent: Int? = null,
  val bmr: Int? = null,
  val metabolicAge: Int? = null,
)
