package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.serialization.Serializable

enum class Gender {
  MALE,
  FEMALE,
}

@Serializable
enum class WeightUnit(
  val value: String,
  val label: String,
  val unit: String,
) {
  KG("kg", "kg", "kg & cm"),
  LB("lb", "lbs", "lbs & feet"),
  ;

  companion object {
    /**
     * Parses a string to a WeightUnit enum.
     * Accepts "kg", "lb", "lbs" (case-insensitive), defaults to LB.
     */
    fun from(value: String?): WeightUnit =
      when (value?.lowercase()?.trim()) {
        KG.value -> KG
        LB.value, "lbs" -> LB
        else -> {
          AppLog.w("WeightUnit", "Unknown weight unit '$value', defaulting to LB")
          LB
        }
      }
  }

  /**
   * Returns the appropriate unit label based on the weight value.
   * For LB: returns "lb" if weight <= 1.0, "lbs" if weight > 1.0
   * For KG: always returns "kg"
   * Special case: When weight is 0.0 (no data), returns "lbs" for LB unit to show plural form
   *
   * @param weight The weight value to determine the appropriate unit label
   * @return The formatted unit string
   */
  fun getDisplayUnit(weight: Double): String {
    return when (this) {
      KG -> "kg"
      LB -> when {
        weight == 0.0 -> "lbs" // Default to plural when no data
        weight <= 1.0 -> "lb"
        else -> "lbs"
      }
    }
  }
}
