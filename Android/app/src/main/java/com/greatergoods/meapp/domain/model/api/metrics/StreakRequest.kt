package com.greatergoods.meapp.domain.model.api.metrics

data class StreakRequest(
    val isStreakOn: Boolean,
    val streakTimestamp: String?
)
