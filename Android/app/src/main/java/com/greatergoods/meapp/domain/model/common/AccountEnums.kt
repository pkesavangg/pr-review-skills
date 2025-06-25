package com.greatergoods.meapp.domain.model.common

enum class Gender {
    MALE,
    FEMALE
}

enum class WeightUnit(val value: String) {
    KG("kg"),
    LB("lb")
}

enum class ActivityLevel {
    NORMAL,
    ATHLETE
}

enum class DashboardType(val value: String) {
    DASHBOARD_4_METRICS("dashboard_4_metrics"),
    DASHBOARD_12_METRICS("dashboard_12_metrics")
}
