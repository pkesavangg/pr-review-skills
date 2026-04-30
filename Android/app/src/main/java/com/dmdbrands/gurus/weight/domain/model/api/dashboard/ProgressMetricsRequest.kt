package com.dmdbrands.gurus.weight.domain.model.api.dashboard

/**
 * Request model for updating progress metrics.
 * @property progressMetrics List of progress metric types. Valid values:
 * - "goal"
 * - "currentStreak"
 * - "longestStreak"
 * - "weeklyChange"
 * - "monthlyChange"
 * - "yearlyChange"
 * - "totalChange"
 */
data class ProgressMetricsRequest(
    val progressMetrics: List<String> // List of progress metric types
)

