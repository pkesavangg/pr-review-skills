package com.dmdbrands.gurus.weight.domain.model.api.metrics

data class StreakRequest(
    val isStreakOn: Boolean,
    val streakTimestamp: String?
)
