package com.greatergoods.meapp.domain.model.common

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog

enum class Gender {
    MALE,
    FEMALE,
}

enum class WeightUnit(
    val value: String,
    val label: String,
) {
    KG("kg", "kg"),
    LB("lb", "lbs"),
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

enum class ActivityLevel {
    NORMAL,
    ATHLETE,
}

enum class DashboardType(
    val value: String,
) {
    DASHBOARD_4_METRICS("dashboard_4_metrics"),
    DASHBOARD_12_METRICS("dashboard_12_metrics"),
}
