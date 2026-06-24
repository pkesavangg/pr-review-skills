package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.processWeight
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.Streak
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Data class combining weight unit and weightless settings for efficient flow operations.
 */
data class WeightSettings(
    val weightUnit: WeightUnit?,
    val weightless: Weightless?,
    val goal: Goal?,
)

/** Holder for the five flows combined for progress; used to combine with progressCacheVersion. */
internal data class ProgressFlowInputs(
    val latest: Entry?,
    val last7: List<Entry>,
    val last30: List<Entry>,
    val weightSettings: WeightSettings,
    val monthYear: List<HistoryMonth>,
)

/**
 * Pure in-memory progress calculation. No DB or repository calls.
 * All inputs are pre-computed by the caller; this function only does arithmetic.
 */
internal fun calculateProgressPure(
    latestEntry: Entry?,
    last7Days: List<Entry>,
    last30Days: List<Entry>,
    months: List<HistoryMonth>,
    startingWeightDisplay: Double?,
    firstRecordedWeightDisplay: Double?,
    currentStreak: Int,
    longestStreak: Int,
    totalCount: Int,
    unit: WeightUnit,
    goal: Goal?,
): WeightProgress {
    var week: Double? = null
    var initWeek: Entry? = null
    var month: Double? = null
    var initMonth: Entry? = null
    var year: Double? = null
    var initYear: HistoryMonth? = null
    var total: Double? = null
    initWeek = if (last7Days.isNotEmpty()) last7Days.last() else null
    initMonth = if (last30Days.isNotEmpty()) last30Days.last() else null
    initYear = if (months.isNotEmpty()) months.last() else null
    if (latestEntry != null && initWeek != null && latestEntry is ScaleEntry && initWeek is ScaleEntry) {
        week = latestEntry.scale.scaleEntry.weight.toDouble() - initWeek.scale.scaleEntry.weight.toDouble()
    }
    if (latestEntry != null && initMonth != null && latestEntry is ScaleEntry && initMonth is ScaleEntry) {
        month = latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
    }
    val totalBaselineWeight = firstRecordedWeightDisplay ?: startingWeightDisplay
    if (latestEntry != null && latestEntry is ScaleEntry && totalBaselineWeight != null) {
        total = latestEntry.scale.scaleEntry.weight.toDouble() - totalBaselineWeight
        AppLog.d(
            "EntryService",
            "Total milestone calc -> latest=${latestEntry.scale.scaleEntry.weight}, starting=$startingWeightDisplay, firstRecorded=$firstRecordedWeightDisplay, baseline=$totalBaselineWeight, total=$total",
        )
    }
    val thirtyDaysAgoDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
    if (initYear != null && initYear.entryTimestamp != null) {
        try {
            val yearMonthFormat = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
            val initYearDate = yearMonthFormat.parse(initYear.entryTimestamp)
            val initYearCalendar = Calendar.getInstance().apply {
                if (initYearDate != null) time = initYearDate
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val thirtyDaysAgoDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(thirtyDaysAgoDate.time)
            val initYearDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(initYearCalendar.time)
            if (initYearDateString >= thirtyDaysAgoDateString) {
                if (latestEntry != null && initMonth != null && latestEntry is ScaleEntry && initMonth is ScaleEntry) {
                    year = latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
                }
            } else {
                val avgWeight = initYear.avgWeight
                if (latestEntry != null && avgWeight != null && latestEntry is ScaleEntry) {
                    year = latestEntry.scale.scaleEntry.weight.toDouble() - avgWeight
                }
            }
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error parsing initYear date: ${initYear.entryTimestamp}", e)
            val avgWeight = initYear.avgWeight
            if (latestEntry != null && avgWeight != null && latestEntry is ScaleEntry) {
                year = latestEntry.scale.scaleEntry.weight.toDouble() - avgWeight
            }
        }
    }
    return WeightProgress(
        latest = latestEntry,
        goal = goal,
        streak = Streak(current = currentStreak, longest = longestStreak),
        count = totalCount,
        initWt = totalBaselineWeight ?: 0.0,
        week = week,
        month = month,
        year = year,
        total = total,
        unit = unit,
        initWeek = initWeek,
        initMonth = initMonth,
        initYear = initYear,
    )
}
