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
  KG("kg", "kg", "kg / cm"),
  LB("lb", "lbs", "lbs / in"),
  LB_OZ("lb_oz", "lbs & oz", "lbs & oz / in"),
  ;

  companion object {
    /** Canonical display default — LB is the storage unit, used as the frame-0
     *  fallback before the user's preference loads reactively. */
    val DISPLAY_DEFAULT = LB

    /**
     * Parses a string to a WeightUnit enum.
     * Accepts "kg", "lb", "lbs", "lb_oz" (case-insensitive), defaults to LB.
     */
    fun from(value: String?): WeightUnit =
      when (value?.lowercase()?.trim()) {
        KG.value -> KG
        LB.value, "lbs" -> LB
        LB_OZ.value -> LB_OZ
        else -> {
          AppLog.w("WeightUnit", "Unknown weight unit '$value', defaulting to LB")
          LB
        }
      }
  }

}
