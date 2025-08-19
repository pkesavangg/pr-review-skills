package com.dmdbrands.gurus.weight.features.common.helper.graph

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded
import com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoSource
import com.dmdbrands.gurus.weight.proto.MetricKey
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import kotlin.reflect.KProperty1

val dateRangeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
val monthYearFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d")

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
            y = Label(value = entry.weight, label = "${entry.prefix}${entry.weight.rounded() ?: 0}"),
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

    val prop = metricKeyPropertyMap[metricKey]
      ?: error("Unsupported MetricKey: $metricKey")

    return GraphLine(
      name = metricKey.name,
      points = this.mapNotNull { summary ->
        val value = (prop.get(summary) as? Number)?.toFloat()
        value?.let {
          GraphPoint(
            x = Label(
              value = DateTimeConverter.isoToTimestamp(summary.entryTimestamp),
              label = summary.entryTimestamp,
            ),
            y = Label(value = it, label = "$it"),
          )
        }
      },
    )
  }

  fun getSourceFromSegment(segment: GraphSegment): MetricInfoSource = when (segment) {
    GraphSegment.WEEK, GraphSegment.MONTH -> MetricInfoSource.DAY
    GraphSegment.YEAR, GraphSegment.TOTAL -> MetricInfoSource.MONTH
  }

  private val metricKeyPropertyMap: Map<MetricKey, KProperty1<PeriodBodyScaleSummary, *>> = mapOf(
    MetricKey.BMI to PeriodBodyScaleSummary::bmi,
    MetricKey.BODY_FAT to PeriodBodyScaleSummary::bodyFat,
    MetricKey.MUSCLE_MASS to PeriodBodyScaleSummary::muscleMass,
    MetricKey.BODY_WATER to PeriodBodyScaleSummary::water,
    MetricKey.HEART_RATE to PeriodBodyScaleSummary::pulse,
    MetricKey.BONE_MASS to PeriodBodyScaleSummary::boneMass,
    MetricKey.VISCERAL_FAT to PeriodBodyScaleSummary::visceralFatLevel,
    MetricKey.SUBCUTANEOUS_FAT to PeriodBodyScaleSummary::subcutaneousFatPercent,
    MetricKey.PROTEIN to PeriodBodyScaleSummary::proteinPercent,
    MetricKey.SKELETAL_MUSCLE to PeriodBodyScaleSummary::skeletalMusclePercent,
    MetricKey.BMR to PeriodBodyScaleSummary::bmr,
    MetricKey.METABOLIC_AGE to PeriodBodyScaleSummary::metabolicAge,
  )

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
  fun GraphSegment.intervalCount(): Int =
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
  ): Map<String, Label?> {
    val result = graphLines.associate { line ->

      val yValues =
        line.points
          .filter { it.x.value.toLong() in min..max }
          .map { it.y.value.toDouble() }

      val average =
        if (yValues.isNotEmpty()) {
          yValues.average().toFloat()
        } else {
          null
        }
      val inWeightlessMode = line.points.map { it.y.label }.any { it.contains("+") }
      val prefix = if (inWeightlessMode && average != null && average > 0) "+" else ""

      val label = if (average == null) null else Label(
        value = average.toDouble().rounded() ?: 0.0,
        label = prefix + average.toDouble().rounded().toString(),
      )

      line.name to label
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

  fun getStartRange(segment: GraphSegment, timeStamp: Long): Long? = when (segment) {
    GraphSegment.WEEK -> DateTimeConverter.getWeekRange(timeStamp).start
    GraphSegment.MONTH -> DateTimeConverter.getMonthRange(timeStamp).start
    GraphSegment.YEAR -> DateTimeConverter.getYearRange(timeStamp).start
    else -> null
  }

  fun getEndRange(segment: GraphSegment, timeStamp: Long): Long? = when (segment) {
    GraphSegment.WEEK -> DateTimeConverter.getWeekRange(timeStamp).end
    GraphSegment.MONTH -> DateTimeConverter.getMonthRange(timeStamp).end
    GraphSegment.YEAR -> DateTimeConverter.getYearRange(timeStamp).end
    else -> null
  }

  fun periodStarts(
    segment: GraphSegment,
    startMillis: Long?,
    endMillis: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
    weekStart: DayOfWeek = DayOfWeek.SUNDAY
  ): List<Double> {
    if (startMillis == null || endMillis == null) return emptyList()
    require(startMillis <= endMillis) { "startMillis must be <= endMillis as $startMillis > $endMillis" }

    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

    // Align to the first boundary >= startDate
    var cursor = when (segment) {
      GraphSegment.WEEK -> startDate.with(TemporalAdjusters.nextOrSame(weekStart))
      GraphSegment.MONTH -> startDate.withDayOfMonth(1)
      GraphSegment.YEAR -> startDate.withDayOfYear(1)
      else -> startDate.withDayOfYear(1)
    }
    if (segment == GraphSegment.WEEK && cursor.isBefore(startDate)) {
      cursor = cursor.plusWeeks(1)
    }
    if (segment == GraphSegment.MONTH && cursor.isBefore(startDate)) {
      cursor = cursor.plusMonths(1)
    }
    if (segment == GraphSegment.YEAR && cursor.isBefore(startDate)) {
      cursor = cursor.plusYears(1)
    }

    val lastBoundary = when (segment) {
      GraphSegment.WEEK -> endDate.with(TemporalAdjusters.previousOrSame(weekStart))
      GraphSegment.MONTH -> endDate.withDayOfMonth(1)
      GraphSegment.YEAR -> endDate.withDayOfYear(1)
      else -> endDate.withDayOfYear(1)
    }

    val step = when (segment) {
      GraphSegment.WEEK -> Period.ofWeeks(1)
      GraphSegment.MONTH -> Period.ofMonths(1)
      GraphSegment.YEAR -> Period.ofYears(1)
      else -> Period.ofYears(1)
    }

    val out = mutableListOf<Double>()
    while (!cursor.isAfter(lastBoundary)) {
      val ms = cursor.atStartOfDay(zone).toInstant().toEpochMilli()
      if (ms in startMillis..endMillis) out.add(ms.toDouble())
      cursor = cursor.plus(step)
    }
    return out
  }

  // endregion
}
