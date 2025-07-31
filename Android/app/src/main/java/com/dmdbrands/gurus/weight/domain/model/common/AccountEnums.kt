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
}
