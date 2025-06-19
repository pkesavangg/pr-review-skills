package com.greatergoods.meapp.domain.model.common

/**
 * Data class representing monthly aggregated history data.
 * Used for displaying monthly statistics and trends.
 */
data class HistoryMonth(
    val entryTimestamp: String? = null,
    val avgWeight: Double? = null,
    val entryCount: Int? = null,
    val change: Double? = null,
)
