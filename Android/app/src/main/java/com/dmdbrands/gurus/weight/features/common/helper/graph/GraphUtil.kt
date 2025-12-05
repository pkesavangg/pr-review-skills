package com.dmdbrands.gurus.weight.features.common.helper.graph

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded
import com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoSource
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.Month
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import kotlin.reflect.KProperty1

val dateTimeRangeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy ")
val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
val monthYearFormatter = DateTimeFormatter.ofPattern("MMM yyyy ")
val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d ")

/**
 * Utility object for graph-related data transformation and formatting.
 * Contains only pure, stateless, non-UI logic.
 */
object GraphUtil {
  // region Constants
  /** Number of milliseconds in one day. */
  const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L // 86,400,000 milliseconds

  /**
   * Metric-specific static ranges for normalization fallback when data has minimal variation.
   * These ranges match iOS DashboardConstants.MetricRanges for consistency.
   */
  private object MetricRanges {
    val BMI = 18.0..35.0
    val PERCENTAGE = 0.0..100.0
    val HEART_RATE = 40.0..200.0
    val VISCERAL_FAT = 1.0..30.0
    val BMR = 1000.0..3000.0
    val METABOLIC_AGE = 15.0..80.0
  }

  /**
   * Gets static metric ranges as fallback for cases with minimal data variation.
   * Matches iOS getStaticMetricRange implementation.
   *
   * @param metricKey The metric key to get the range for
   * @return Pair of (min, max) for the metric's static range
   */
  private fun getStaticMetricRange(metricKey: MetricKey): Pair<Double, Double> {
    return when (metricKey) {
      MetricKey.BMI -> Pair(MetricRanges.BMI.start, MetricRanges.BMI.endInclusive)
      MetricKey.BODY_FAT, MetricKey.MUSCLE_MASS, MetricKey.BODY_WATER,
      MetricKey.BONE_MASS, MetricKey.SUBCUTANEOUS_FAT, MetricKey.PROTEIN,
      MetricKey.SKELETAL_MUSCLE -> Pair(MetricRanges.PERCENTAGE.start, MetricRanges.PERCENTAGE.endInclusive)
      MetricKey.HEART_RATE -> Pair(MetricRanges.HEART_RATE.start, MetricRanges.HEART_RATE.endInclusive)
      MetricKey.VISCERAL_FAT -> Pair(MetricRanges.VISCERAL_FAT.start, MetricRanges.VISCERAL_FAT.endInclusive)
      MetricKey.BMR -> Pair(MetricRanges.BMR.start, MetricRanges.BMR.endInclusive)
      MetricKey.METABOLIC_AGE -> Pair(MetricRanges.METABOLIC_AGE.start, MetricRanges.METABOLIC_AGE.endInclusive)
      else -> Pair(MetricRanges.PERCENTAGE.start, MetricRanges.PERCENTAGE.endInclusive) // Default to percentage range
    }
  }
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
        if (value == null || value == 0f) return@mapNotNull null
        value.let {
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
    GraphSegment.WEEK -> MetricInfoSource.WEEK
    GraphSegment.MONTH -> MetricInfoSource.MONTH
    GraphSegment.YEAR -> MetricInfoSource.YEAR
    GraphSegment.TOTAL -> MetricInfoSource.TOTAL
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

  /**
   * Calculates the X-axis step size for the given [GraphSegment].
   * @return The step size in milliseconds.
   */
  fun calculateXStep(
    segment: GraphSegment,
  ): Double =
    when (segment) {
      GraphSegment.WEEK -> ONE_DAY_MILLIS
      GraphSegment.MONTH -> 6 * ONE_DAY_MILLIS
      GraphSegment.YEAR -> 31 * ONE_DAY_MILLIS
      GraphSegment.TOTAL -> 31 * ONE_DAY_MILLIS
    }.toDouble()

  /**
   * Returns the number of intervals for the given [GraphSegment].
   */
  fun GraphSegment.intervalCount(): Double =
    when (this) {
      GraphSegment.WEEK -> 7
      GraphSegment.MONTH -> 5
      GraphSegment.YEAR -> 11.75
      GraphSegment.TOTAL -> 1000
    }.toDouble() - 0.001
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
    // Use local timezone (system default) instead of UTC
    val localZone = ZoneId.systemDefault()
    val localDateTime = Instant.ofEpochMilli(timestamp).atZone(localZone)

    return when (segment) {
      GraphSegment.WEEK, GraphSegment.MONTH -> {
        localDateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
      }

      GraphSegment.YEAR, GraphSegment.TOTAL -> {
        localDateTime.format(DateTimeFormatter.ofPattern("MMM yyyy"))
      }
    }
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

  fun getImmediateAvailablePoint(graphLines: GraphLine, timeStamp: Long, isSecondary: Boolean): Long? {
    val immediatePoint = graphLines.points.firstOrNull { it.x.value.toLong() > timeStamp }
    return immediatePoint?.y?.value?.toLong()
  }

  fun getPreviousAvailablePoint(graphLines: GraphLine, timeStamp: Long, isSecondary: Boolean): Long? {
    val previousPoint = graphLines.points.lastOrNull { it.x.value.toLong() < timeStamp }
    return previousPoint?.y?.value?.toLong()
  }

  /**
   * Normalizes secondary metric values to the weight Y-axis range (iOS-style normalization).
   * Maps metric values proportionally to the weight scale for visual comparison.
   * Matches iOS generateNormalizedMetricSeriesWithDomain implementation.
   *
   * Formula: normalizedValue = weightMin + (clampedValue - effectiveMetricMin) ×
   *                            (weightMax - weightMin) / (effectiveMetricMax - effectiveMetricMin)
   *
   * @param metricGraphLine The secondary metric graph line to normalize
   * @param weightMin Minimum value of weight Y-axis domain
   * @param weightMax Maximum value of weight Y-axis domain
   * @param minX Minimum X timestamp for visible range
   * @param maxX Maximum X timestamp for visible range
   * @param metricKey The metric key to determine static ranges for single points (iOS-style).
   *                  If null, falls back to fixed padding (backward compatibility).
   * @return GraphLine with normalized Y values mapped to weight scale
   */
  fun normalizeMetricToWeightRange(
    metricGraphLine: GraphLine,
    weightMin: Double,
    weightMax: Double,
    minX: Long,
    maxX: Long,
    metricKey: MetricKey? = null
  ): GraphLine {
    if (metricGraphLine.points.isEmpty()) {
      return metricGraphLine
    }

    // Validate input parameters are finite (matching iOS defensive checks)
    if (!weightMin.isFinite() || !weightMax.isFinite() || weightMin >= weightMax) {
      return metricGraphLine
    }

    // Get all metric values (including previous/next for range calculation)
    val allMetricValues = metricGraphLine.points.mapNotNull { it.y.value as? Number }
      .map { it.toDouble() }
      .filter { it.isFinite() } // Filter out NaN/Infinity values

    if (allMetricValues.isEmpty()) {
      return metricGraphLine
    }

    // Get visible and bracketing points for range calculation
    val visiblePoints = metricGraphLine.points.filter {
      it.x.value.toLong() in minX..maxX
    }
    val previousPoint = getPreviousAvailablePoint(metricGraphLine, minX, false)
    val nextPoint = getImmediateAvailablePoint(metricGraphLine, maxX, false)

    // Collect metric values for range calculation (visible + bracketing)
    // val metricValuesForRange = buildList {
    //   previousPoint?.let { val d = it.toDouble(); if (d.isFinite()) add(d) }
    //   addAll(visiblePoints.mapNotNull { (it.y.value as? Number)?.toDouble() }.filter { it.isFinite() })
    //   nextPoint?.let { val d = it.toDouble(); if (d.isFinite()) add(d) }
    // }

    val metricValuesForRange = buildList {
      previousPoint?.let { add(it.toDouble()) }
      addAll(visiblePoints.mapNotNull { (it.y.value as? Number)?.toDouble() })
      nextPoint?.let { add(it.toDouble()) }
    }

    if (metricValuesForRange.isEmpty()) {
      return metricGraphLine
    }

    // Calculate metric range
    val metricMin = metricValuesForRange.minOrNull() ?: return metricGraphLine
    val metricMax = metricValuesForRange.maxOrNull() ?: return metricGraphLine

    val metricRange = metricMax - metricMin
    // Handle single point or minimal variation (matching iOS: metricRange < 0.01)
    val isSingleMetricPoint = metricRange < 0.01
    val effectiveMetricMin: Double
    val effectiveMetricMax: Double

    if (isSingleMetricPoint) {
      // Use metric-specific static ranges for single points (matching iOS getStaticMetricRange)
      // This provides realistic bounds for normalization when data has minimal variation
      if (metricKey != null) {
        val (staticMin, staticMax) = getStaticMetricRange(metricKey)
        // Use the wider range between actual data and static range (matching iOS)
        effectiveMetricMin = minOf(metricMin, staticMin)
        effectiveMetricMax = maxOf(metricMax, staticMax)
      } else {
        // Fallback to fixed padding if metricKey is not provided (backward compatibility)
        val padding = 1.0
        effectiveMetricMin = metricMin - padding
        effectiveMetricMax = metricMax + padding
      }
    } else {
      // Add 5% padding (matching iOS implementation)
      val padding = metricRange * 0.05
      effectiveMetricMin = metricMin - padding
      effectiveMetricMax = metricMax + padding
    }

    val metricRangeSpan = effectiveMetricMax - effectiveMetricMin
    if (metricRangeSpan <= 0) {
      return metricGraphLine
    }

    val yAxisSpan = weightMax - weightMin
    val epsilon = yAxisSpan * 0.001 // 0.1% margin for safety bounds

    // Calculate safe fallback value once (middle of weight range)
    // Validate it's finite to use as fallback (matching iOS defensive checks)
    val safeFallbackValue = (weightMin + weightMax) / 2.0
    val useFallback = safeFallbackValue.isFinite()

    // Normalize each point
    val normalizedPoints = metricGraphLine.points.mapNotNull { point ->
      val metricValue = (point.y.value as? Number)?.toDouble()

      // Skip points with null or non-finite metric values (matching iOS: skip missing/invalid values)
      if (metricValue == null || !metricValue.isFinite()) {
        return@mapNotNull null
      }

      // For single points, place at fixed position (60% of Y-axis height, matching iOS)
      if (isSingleMetricPoint) {
        val positionInRange = weightMin + (yAxisSpan * 0.6)
        // Validate position is finite before using (matching iOS guard checks)
        if (positionInRange.isFinite()) {
          point.copy(
            y = point.y.copy(value = positionInRange)
          )
        } else if (useFallback) {
          // Fallback to middle of weight range (validated above)
          point.copy(
            y = point.y.copy(value = safeFallbackValue)
          )
        } else {
          // If fallback is also invalid, skip this point entirely
          null
        }
      } else {
        // Clamp value to effective range
        val clampedValue = maxOf(effectiveMetricMin, minOf(effectiveMetricMax, metricValue))

        // Normalize to weight range
        val normalizedValue = weightMin + (clampedValue - effectiveMetricMin) *
                              yAxisSpan / metricRangeSpan

        // Apply safety bounds (keep slightly inside bounds)
        val safeMin = weightMin + epsilon
        val safeMax = weightMax - epsilon
        val finalValue = maxOf(safeMin, minOf(safeMax, normalizedValue))

        // Ensure finite value (matching iOS guard checks)
        if (finalValue.isFinite()) {
          point.copy(
            y = point.y.copy(value = finalValue)
          )
        } else if (useFallback) {
          // Fallback to middle of weight range (validated above)
          point.copy(
            y = point.y.copy(value = safeFallbackValue)
          )
        } else {
          // If fallback is also invalid, skip this point entirely
          null
        }
      }
    }

    return metricGraphLine.copy(points = normalizedPoints)
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

  fun MetricInfoSource.toSegment(): GraphSegment {
    return when (this) {
      MetricInfoSource.WEEK -> GraphSegment.WEEK
      MetricInfoSource.MONTH -> GraphSegment.MONTH
      MetricInfoSource.YEAR -> GraphSegment.YEAR
      MetricInfoSource.TOTAL -> GraphSegment.TOTAL
    }
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
   * Formats a date range for the given [GraphSegment] including time information.
   * @return A formatted string representing the date and time range.
   */
  fun formatDateRange(
    startTimestamp: Long,
    endTimestamp: Long,
    segment: GraphSegment,
  ): String {
    val zone = ZoneId.systemDefault()
    val startDateTime = Instant.ofEpochMilli(startTimestamp).atZone(zone)
    val endDateTime = Instant.ofEpochMilli(endTimestamp).atZone(zone)
    val startDate = startDateTime.toLocalDate()
    val endDate = endDateTime.toLocalDate()

    return when (segment) {
      GraphSegment.YEAR -> {
        if (startDate.month == Month.JANUARY) {
          "${startDateTime.format(yearFormatter)}"
        } else {
          "${startDateTime.format(monthYearFormatter)} – ${endDateTime.format(monthYearFormatter)}"
        }
      }

      GraphSegment.TOTAL -> {
        "${startDateTime.format(monthYearFormatter)} – ${endDateTime.format(monthYearFormatter)}"
      }

      GraphSegment.MONTH -> {
        if (startDate.dayOfMonth == 1) {
          "${startDateTime.format(monthYearFormatter)}"
        } else {
          when {
            startDate.year != endDate.year -> {
              "${startDateTime.format(dateTimeRangeFormatter)} – ${endDateTime.format(dateTimeRangeFormatter)}"
            }

            startDate.month != endDate.month -> {
              "${startDateTime.format(monthDayFormatter)} – ${endDateTime.format(dateTimeRangeFormatter)}"
            }

            else -> {
              "${startDateTime.format(monthDayFormatter)} – ${endDateTime.dayOfMonth}, ${startDate.year}"
            }
          }
        }
      }

      GraphSegment.WEEK -> {
        when {
          startDate.year != endDate.year -> {
            "${startDateTime.format(dateTimeRangeFormatter)} – ${endDateTime.format(dateTimeRangeFormatter)}"
          }

          startDate.month != endDate.month -> {
            "${startDateTime.format(monthDayFormatter)} – ${endDateTime.format(dateTimeRangeFormatter)}"
          }

          else -> {
            "${startDateTime.format(monthDayFormatter)} – ${endDateTime.dayOfMonth}, ${startDate.year}"
          }
        }
      }
    }
  }

  /**
   * Gets the start timestamp for the given graph segment.
   * @param segment The graph segment (WEEK, MONTH, YEAR, TOTAL)
   * @param timeStamp Reference timestamp in milliseconds
   * @return Start timestamp for the segment, or null if timeStamp is null
   */
  fun getStartRange(segment: GraphSegment, timeStamp: Long?): Long? = timeStamp?.let {
    when (segment) {
      GraphSegment.WEEK -> DateTimeConverter.getWeekStart(timeStamp)
      GraphSegment.MONTH -> DateTimeConverter.getMonthStart(timeStamp)
      GraphSegment.YEAR, GraphSegment.TOTAL -> DateTimeConverter.getYearStart(timeStamp)
    }
  }

  /**
   * Gets the end timestamp for the given graph segment.
   * @param segment The graph segment (WEEK, MONTH, YEAR, TOTAL)
   * @param timeStamp Reference timestamp in milliseconds
   * @return End timestamp for the segment, or null if timeStamp is null
   */
  fun getEndRange(segment: GraphSegment, timeStamp: Long?): Long? = timeStamp?.let {
    when (segment) {
      GraphSegment.WEEK -> DateTimeConverter.getWeekEnd(timeStamp)
      GraphSegment.MONTH -> DateTimeConverter.getMonthEnd(timeStamp)
      GraphSegment.YEAR, GraphSegment.TOTAL -> DateTimeConverter.getYearEnd(timeStamp)
    }
  }

  fun periodStarts(
    segment: GraphSegment,
    startMillis: Long?,
    endMillis: Long?
  ): List<Long> {
    if (startMillis == null || endMillis == null || startMillis > endMillis || segment == GraphSegment.TOTAL) return emptyList()

    val zone = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

    var cursor = when (segment) {
      GraphSegment.WEEK -> startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
      GraphSegment.MONTH -> startDate.withDayOfMonth(1)
      GraphSegment.YEAR -> startDate.withDayOfYear(1)
      GraphSegment.TOTAL -> startDate.withDayOfYear(1)
    }

    val step = when (segment) {
      GraphSegment.WEEK -> Period.ofWeeks(1)
      GraphSegment.MONTH -> Period.ofMonths(1)
      else -> Period.ofYears(1)
    }

    val out = ArrayList<Long>()
    while (!cursor.isAfter(endDate)) {
      // 👇 Local midnight millis (wall-clock IST)
      val ms = cursor.atStartOfDay(zone).toEpochSecond() * 1000
      out.add(ms)
      cursor = cursor.plus(step)
    }
    return out
  }

  /**
   * Alternative implementation that simply calculates months between years.
   * More efficient for large year ranges.
   *
   * @param startTimeMillis Start timestamp in milliseconds
   * @param endTimeMillis End timestamp in milliseconds
   * @return Total number of months between the years of the timestamps
   */
  fun getTotalMonthsBetweenYears(startTimeMillis: Long, endTimeMillis: Long): Int {
    if (startTimeMillis > endTimeMillis) {
      return 0
    }

    val localZone = ZoneId.systemDefault()

    val startYear = Instant.ofEpochMilli(startTimeMillis).atZone(localZone).year
    val endYear = Instant.ofEpochMilli(endTimeMillis).atZone(localZone).year

    // Calculate total months between years
    return ((endYear - startYear + 1) * 12)
  }

  // endregion
}
