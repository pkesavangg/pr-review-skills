package com.dmdbrands.gurus.weight.domain.model.common

/**
 * A single baby history entry row within a week.
 */
data class BabyWeekHistory(
    val date: String,
    // Machine date key (YYYY-MM-DD) passed to the day-detail screen; `date` is display-only.
    val dateKey: String,
    val entryCount: Int,
    // Raw canonical values; the row formats them into the account's My Kids unit (lb-oz / lb / kg,
    // in / cm) at render time so a unit change reflects without rebuilding the model. (MOB-1499)
    val weightDecigrams: Int?,
    val lengthMillimeters: Int?,
    val percentile: Int?,
)

/**
 * A week group containing a label and its entries.
 */
data class BabyWeekGroup(
    val weekLabel: String,
    val entries: List<BabyWeekHistory>,
)
