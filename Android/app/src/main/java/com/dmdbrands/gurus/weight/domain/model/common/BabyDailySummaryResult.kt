package com.dmdbrands.gurus.weight.domain.model.common

/**
 * Room query result type for baby daily aggregated data.
 * SQL groups baby entries by day and calculates averages.
 * Repository converts this to [BabyWeekHistory] and groups by week into [BabyWeekGroup].
 */
data class BabyDailySummaryResult(
    val date: String,
    val entryCount: Int,
    val babyWeightDecigrams: Int?,
    val babyLengthMillimeters: Int?,
    val weekNumber: Int,
    val year: Int,
)
