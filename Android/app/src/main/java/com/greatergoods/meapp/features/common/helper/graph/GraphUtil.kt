package com.greatergoods.meapp.features.common.helper.graph

import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.features.common.enums.GraphSegment
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.proto.DashboardKey
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.reflect.KProperty1
import android.util.Log

/**
 * Utility object for graph-related data transformation and formatting.
 * Contains only pure, stateless, non-UI logic.
 */
object GraphUtil {
    // region Constants
    /** Number of milliseconds in one day. */
    const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L // 86,400,000 milliseconds
    // endregion

    // region Cached Formatters
    private val dateRangeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
    private val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val weekFormatter = SimpleDateFormat("EEE", Locale.ENGLISH)
    private val dayFormatter = SimpleDateFormat("d", Locale.ENGLISH)
    private val monthFormatter = SimpleDateFormat("MMM", Locale.ENGLISH)
    // endregion

    // region Data Transformation

    /**
     * Converts a list of [PeriodBodyScaleSummary] to a [GraphLine] for weight.
     * @return [GraphLine] representing weight over time.
     */
    fun List<PeriodBodyScaleSummary>.toWeightGraphPoints(): GraphLine =
        GraphLine(
            name = "Weight",
            points =
                this.map { entry ->
                    GraphPoint(
                        x =
                            Label(
                                value = DateTimeConverter.isoToTimestamp(entry.entryTimestamp),
                                label = entry.period,
                            ),
                        y = Label(value = entry.weight, label = "${entry.weight} kg"),
                    )
                },
        )

    /**
     * Converts a list of [ScaleEntry] to a [GraphLine] for the given property name.
     * Uses reflection to extract the property value from [BodyScaleEntryEntity] or [BodyScaleEntryMetricEntity].
     * @param propertyName The property to extract (e.g., "weight").
     * @return [GraphLine] for the property.
     */
    fun List<PeriodBodyScaleSummary>.toGraphPoints(metricKey: MetricKey): GraphLine {
        val prop: KProperty1<PeriodBodyScaleSummary, *>? =
            when (metricKey) {
                MetricKey.BMI -> PeriodBodyScaleSummary::bmi
                MetricKey.BODY_FAT -> PeriodBodyScaleSummary::bodyFat
                MetricKey.MUSCLE_MASS -> PeriodBodyScaleSummary::muscleMass
                MetricKey.BODY_WATER -> PeriodBodyScaleSummary::water
                MetricKey.HEART_RATE -> PeriodBodyScaleSummary::pulse
                MetricKey.BONE_MASS -> PeriodBodyScaleSummary::boneMass
                MetricKey.VISCERAL_FAT -> PeriodBodyScaleSummary::visceralFatLevel
                MetricKey.SUBCUTANEOUS_FAT -> PeriodBodyScaleSummary::subcutaneousFatPercent
                MetricKey.PROTEIN -> PeriodBodyScaleSummary::proteinPercent
                MetricKey.SKELETAL_MUSCLE -> PeriodBodyScaleSummary::skeletalMusclePercent
                MetricKey.BMR -> PeriodBodyScaleSummary::bmr
                MetricKey.METABOLIC_AGE -> PeriodBodyScaleSummary::metabolicAge
            }

        return GraphLine(
            name = metricKey.name,
            points =
                this.mapNotNull { summary ->
                    val value = (prop?.get(summary) as? Number)?.toFloat()
                    value?.let {
                        GraphPoint(
                            x =
                                Label(
                                    value = DateTimeConverter.isoToTimestamp(summary.entryTimestamp),
                                    label = summary.entryTimestamp,
                                ),
                            y = Label(value = it, label = "$it"),
                        )
                    }
                },
        )
    }

    // endregion

    // region Calculation

    /**
     * Calculates the X-axis step size for the given [GraphSegment].
     * @return The step size in milliseconds.
     */
    fun calculateXStep(
        segment: GraphSegment,
        xLabels: List<Double>,
    ): Double =
        when (segment) {
            GraphSegment.WEEK -> ONE_DAY_MILLIS
            GraphSegment.MONTH -> 5 * ONE_DAY_MILLIS
            GraphSegment.YEAR -> 31 * ONE_DAY_MILLIS
            GraphSegment.TOTAL -> {
                ONE_DAY_MILLIS * 60
            }
        }.toDouble()

    fun List<Double>.getMinPositiveDelta(): Double {
        if (size < 2) return 1.0
        val sorted = sorted()
        return sorted
            .zipWithNext { a, b -> b - a }
            .filter { it > 0 }
            .minOrNull() ?: 1.0
    }

    /**
     * Returns the number of intervals for the given [GraphSegment].
     */
    private fun GraphSegment.intervalCount(): Int =
        when (this) {
            GraphSegment.WEEK -> 7
            GraphSegment.MONTH -> 6
            GraphSegment.YEAR -> 12
            else -> 32
        }
    // endregion

    // region Formatting

    /**
     * Formats a timestamp for the given [GraphSegment].
     * @return A formatted string for axis labels.
     */
    fun formatTimestampForSegment(
        timestamp: Long,
        segment: GraphSegment,
    ): String {
        val date = Date(timestamp)
        val formatter =
            when (segment) {
                GraphSegment.WEEK -> weekFormatter
                GraphSegment.MONTH -> dayFormatter
                GraphSegment.YEAR, GraphSegment.TOTAL -> monthFormatter
            }
        val result = formatter.format(date)
        return if (segment == GraphSegment.YEAR) result.take(1) else result
    }

    fun markerValueFormatter(
        timestamp: Long,
        segment: GraphSegment,
    ): String {
        val formatter =
            when (segment) {
                GraphSegment.WEEK, GraphSegment.MONTH -> dateRangeFormatter
                GraphSegment.YEAR, GraphSegment.TOTAL -> monthYearFormatter
            }
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        return startDate.format(formatter)
    }

    fun filterXValuesInRange(
        graphLines: List<GraphLine>,
        min: Long,
        max: Long,
    ): List<GraphLine> =
        graphLines.map { line ->
            line.copy(
                points = line.points.filter { it.x.value.toLong() in min..max },
            )
        }

    fun averageYValuesInRange(
        graphLines: List<GraphLine>,
        min: Long,
        max: Long,
    ): Map<String, Float?> {
        val result =
            graphLines.associate { line ->

                val yValues =
                    line.points
                        .filter { it.x.value.toLong() in min..max }
                        .map { it.y.value.toDouble() }

                Log.d("GraphView", "Average Y values for ${line.name}: $yValues")
                val average =
                    if (yValues.isNotEmpty()) {
                        yValues.average().toFloat()
                    } else {
                        null
                    }

                line.name to average
            }
        return result
    }

    fun averageSummary(metrics: List<PeriodBodyScaleSummary>): PeriodBodyScaleSummary? {
        if (metrics.isEmpty()) return null

        val size = metrics.size.toDouble()

        return PeriodBodyScaleSummary(
            period = "average",
            entryTimestamp = metrics.maxByOrNull { it.entryTimestamp }?.entryTimestamp.orEmpty(),
            weight = metrics.sumOf { it.weight } / size,
            bodyFat = metrics.mapNotNull { it.bodyFat }.averageOrNull(),
            muscleMass = metrics.mapNotNull { it.muscleMass }.averageOrNull(),
            water = metrics.mapNotNull { it.water }.averageOrNull(),
            bmi = metrics.mapNotNull { it.bmi }.averageOrNull(),
            bmr = metrics.mapNotNull { it.bmr }.averageOrNull(),
            metabolicAge = metrics.mapNotNull { it.metabolicAge }.averageOrNull(),
            proteinPercent = metrics.mapNotNull { it.proteinPercent }.averageOrNull(),
            pulse = metrics.mapNotNull { it.pulse }.averageOrNull(),
            skeletalMusclePercent = metrics.mapNotNull { it.skeletalMusclePercent }.averageOrNull(),
            subcutaneousFatPercent = metrics.mapNotNull { it.subcutaneousFatPercent }.averageOrNull(),
            visceralFatLevel = metrics.mapNotNull { it.visceralFatLevel }.averageOrNull(),
            boneMass = metrics.mapNotNull { it.boneMass }.averageOrNull(),
            impedance = metrics.mapNotNull { it.impedance }.averageOrNull(),
            unit = metrics.first().unit,
        )
    }

    // Extension for safe nullable average
    fun List<Double>.averageOrNull(): Double? = if (isNotEmpty()) average().rounded() else null

    /**
     * Formats a date range for the given [GraphSegment].
     * @return A formatted string representing the date range.
     */
    fun formatDateRange(
        startTimestamp: Long,
        endTimestamp: Long,
        segment: GraphSegment,
    ): String {
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(startTimestamp).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endTimestamp).atZone(zone).toLocalDate()
        return when (segment) {
            GraphSegment.YEAR, GraphSegment.TOTAL -> {
                "${startDate.format(monthYearFormatter)} – ${endDate.format(monthYearFormatter)}"
            }

            GraphSegment.MONTH, GraphSegment.WEEK -> {
                when {
                    startDate.year != endDate.year -> {
                        "${startDate.format(dateRangeFormatter)} – ${endDate.format(dateRangeFormatter)}"
                    }

                    startDate.month != endDate.month -> {
                        "${startDate.format(monthDayFormatter)} – ${endDate.format(dateRangeFormatter)}"
                    }

                    else -> {
                        "${startDate.format(monthDayFormatter)} – ${endDate.dayOfMonth}, ${startDate.year}"
                    }
                }
            }
        }
    }
    // endregion
}
