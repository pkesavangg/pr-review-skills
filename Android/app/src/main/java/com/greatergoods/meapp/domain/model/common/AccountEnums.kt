package com.greatergoods.meapp.domain.model.common

enum class Sex {
    MALE,
    FEMALE
}

enum class WeightUnit {
    KG,
    LB
}

enum class ActivityLevel {
    NORMAL,
    ATHLETE
}

enum class DashboardType(val value: String) {
    DASHBOARD_4_METRICS("dashboard_4_metrics"),
    DASHBOARD_12_METRICS("dashboard_12_metrics")
} 