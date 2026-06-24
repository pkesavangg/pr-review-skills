package com.dmdbrands.gurus.weight.domain.enums

/**
 * Blood pressure severity classification per AHA guidelines.
 */
enum class BpSeverity {
  NORMAL,
  ELEVATED,
  HYPERTENSION;

  companion object {
    fun from(systolic: Int, diastolic: Int): BpSeverity = when {
      systolic < 120 && diastolic < 80 -> NORMAL
      systolic in 120..129 && diastolic < 80 -> ELEVATED
      else -> HYPERTENSION
    }
  }
}
