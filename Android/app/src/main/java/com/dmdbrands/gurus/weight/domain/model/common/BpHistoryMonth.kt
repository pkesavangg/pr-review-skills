package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.domain.enums.BpSeverity

/**
 * Monthly aggregated blood pressure summary for history display.
 */
data class BpHistoryMonth(
    val entryTimestamp: String,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int,
    val entryCount: Int,
) {
    val severity: BpSeverity get() = BpSeverity.from(avgSystolic, avgDiastolic)
    val pressureDisplay: String get() = "$avgSystolic/$avgDiastolic"
}
