package com.dmdbrands.gurus.weight.domain.model.common

/**
 * A single baby history entry row within a week.
 */
data class BabyWeekHistory(
    val date: String,
    // Machine date key (YYYY-MM-DD) passed to the day-detail screen; `date` is display-only.
    val dateKey: String,
    val entryCount: Int,
    val weightLb: Int?,
    val weightOz: Double?,
    val lengthInches: Double?,
    val percentile: Int?,
)

/**
 * A week group containing a label and its entries.
 */
data class BabyWeekGroup(
    val weekLabel: String,
    val entries: List<BabyWeekHistory>,
)
