package com.dmdbrands.gurus.weight.features.common.helper

import android.content.Context
import com.dmdbrands.gurus.weight.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * WHO weight-for-age and length-for-age percentile lookup from CSV data.
 * CSV format: Day,5th,10th,25th,50th,75th,90th,95th.
 * Weight values in decigrams, length values in mm.
 * Rows are every 7 days from birth to ~18 years.
 *
 * Also hosts the point-percentile calculator ([calcPercentile]) that uses separate
 * M/SD reference CSVs (one row per age-in-days, columns `Day,M,SD`) together with
 * [BabyZTable] to convert a raw measurement into an integer percentile — port of
 * babyApp's `PercentileService.calcMeasurementPercentile`.
 */
object BabyPercentileHelper {

  /** Which product an M/SD lookup is for — weight (decigrams) or length (mm). */
  enum class MeasurementType { WEIGHT, LENGTH }

  data class PercentileRow(
    val day: Int,
    val p5: Double,
    val p10: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p90: Double,
    val p95: Double,
  )

  /** One row of the CDC mean/stddev reference tables. */
  private data class MeasurementRow(
    val day: Int,
    val m: Double,
    val sd: Double,
  )

  private var boyWeightData: List<PercentileRow>? = null
  private var girlWeightData: List<PercentileRow>? = null
  private var boyLengthData: List<PercentileRow>? = null
  private var girlLengthData: List<PercentileRow>? = null

  // M/SD reference data for point-percentile lookup.
  private var boyWeightMeasurements: List<MeasurementRow>? = null
  private var girlWeightMeasurements: List<MeasurementRow>? = null
  private var boyLengthMeasurements: List<MeasurementRow>? = null
  private var girlLengthMeasurements: List<MeasurementRow>? = null

  /**
   * iOS "dataResolution" — how far to either side of the requested day we look for
   * matching CSV rows. Rows are ~7–8 days apart; a 14-day window catches 1–2 of them.
   */
  private const val DATA_RESOLUTION_DAYS = 7
  private const val MS_PER_DAY = 86_400_000L

  @Synchronized
  fun loadIfNeeded(context: Context) {
    if (boyWeightData == null) boyWeightData = parseCsv(context, R.raw.boy_weight_percentiles)
    if (girlWeightData == null) girlWeightData = parseCsv(context, R.raw.girl_weight_percentiles)
    if (boyLengthData == null) boyLengthData = parseCsv(context, R.raw.boy_length_percentiles)
    if (girlLengthData == null) girlLengthData = parseCsv(context, R.raw.girl_length_percentiles)

    if (boyWeightMeasurements == null) boyWeightMeasurements = parseMeasurementCsv(context, R.raw.boy_weight_decigrams)
    if (girlWeightMeasurements == null) girlWeightMeasurements = parseMeasurementCsv(context, R.raw.girl_weight_decigrams)
    if (boyLengthMeasurements == null) boyLengthMeasurements = parseMeasurementCsv(context, R.raw.boy_length_mm)
    if (girlLengthMeasurements == null) girlLengthMeasurements = parseMeasurementCsv(context, R.raw.girl_length_mm)
  }

  data class PercentileSeries(
    val xTimestamps: List<Double>,
    val p5: List<Double>,
    val p10: List<Double>,
    val p25: List<Double>,
    val p50: List<Double>,
    val p75: List<Double>,
    val p90: List<Double>,
    val p95: List<Double>,
  ) {
    /** All 7 bands as a list of series (for passing to lineSeries) */
    fun allBands(): List<List<Double>> = listOf(p5, p10, p25, p50, p75, p90, p95)
  }

  /**
   * Returns all 7 WHO weight percentile curves from birth to end of CSV data.
   * Values converted from decigrams to lbs.
   */
  fun getWeightPercentileSeries(
    sex: String?,
    birthDateMillis: Long,
  ): PercentileSeries? {
    val data = when (sex?.lowercase()) {
      "male" -> boyWeightData
      "female" -> girlWeightData
      else -> null
    } ?: return null

    return buildPercentileSeries(data, birthDateMillis) { it / 283.495 / 16.0 }
  }

  /**
   * Returns all 7 WHO length percentile curves from birth to end of CSV data.
   * Values converted from mm to inches.
   */
  fun getLengthPercentileSeries(
    sex: String?,
    birthDateMillis: Long,
  ): PercentileSeries? {
    val data = when (sex?.lowercase()) {
      "male" -> boyLengthData
      "female" -> girlLengthData
      else -> null
    } ?: return null

    return buildPercentileSeries(data, birthDateMillis) { it / 25.4 }
  }

  @Deprecated("Use getWeightPercentileSeries instead", ReplaceWith("getWeightPercentileSeries(sex, birthDateMillis)"))
  fun getPercentileSeries(
    sex: String?,
    birthDateMillis: Long,
  ): PercentileSeries? = getWeightPercentileSeries(sex, birthDateMillis)

  private fun buildPercentileSeries(
    data: List<PercentileRow>,
    birthDateMillis: Long,
    convert: (Double) -> Double,
  ): PercentileSeries? {
    val dayMs = 86_400_000L
    val xTimestamps = mutableListOf<Double>()
    val p5 = mutableListOf<Double>()
    val p10 = mutableListOf<Double>()
    val p25 = mutableListOf<Double>()
    val p50 = mutableListOf<Double>()
    val p75 = mutableListOf<Double>()
    val p90 = mutableListOf<Double>()
    val p95 = mutableListOf<Double>()

    for (row in data) {
      val timestamp = (birthDateMillis + row.day.toLong() * dayMs).toDouble()
      xTimestamps.add(timestamp)
      p5.add(convert(row.p5))
      p10.add(convert(row.p10))
      p25.add(convert(row.p25))
      p50.add(convert(row.p50))
      p75.add(convert(row.p75))
      p90.add(convert(row.p90))
      p95.add(convert(row.p95))
    }

    return if (xTimestamps.size >= 2) PercentileSeries(xTimestamps, p5, p10, p25, p50, p75, p90, p95) else null
  }

  private fun parseCsv(context: Context, resId: Int): List<PercentileRow> {
    val rows = mutableListOf<PercentileRow>()
    context.resources.openRawResource(resId).use { stream ->
      BufferedReader(InputStreamReader(stream)).use { reader ->
        reader.readLine() // skip header
        reader.forEachLine { line ->
          val parts = line.split(",")
          if (parts.size >= 8) {
            rows.add(
              PercentileRow(
                day = parts[0].toInt(),
                p5 = parts[1].toDouble(),
                p10 = parts[2].toDouble(),
                p25 = parts[3].toDouble(),
                p50 = parts[4].toDouble(),
                p75 = parts[5].toDouble(),
                p90 = parts[6].toDouble(),
                p95 = parts[7].toDouble(),
              ),
            )
          }
        }
      }
    }
    return rows
  }

  private fun parseMeasurementCsv(context: Context, resId: Int): List<MeasurementRow> {
    val rows = mutableListOf<MeasurementRow>()
    context.resources.openRawResource(resId).use { stream ->
      BufferedReader(InputStreamReader(stream)).use { reader ->
        reader.readLine() // skip header "Day,M,SD"
        reader.forEachLine { line ->
          val parts = line.split(",")
          if (parts.size >= 3) {
            rows.add(
              MeasurementRow(
                day = parts[0].toInt(),
                m = parts[1].toDouble(),
                sd = parts[2].toDouble(),
              ),
            )
          }
        }
      }
    }
    return rows
  }

  // ─── Point-percentile lookup (port of iOS PercentileService.calcMeasurementPercentile) ───

  /**
   * Current CDC percentile (0–100) for a single measurement.
   *
   * @param sex `"male"` / `"female"` — anything else (including `"private"` or null)
   *   returns null.
   * @param birthDateMillis Baby's date of birth as epoch millis.
   * @param value Measurement value. Weight in decigrams, length in millimeters.
   * @param type [MeasurementType.WEIGHT] or [MeasurementType.LENGTH].
   * @param entryTimestampMillis When the measurement was taken (determines the
   *   baby's age for the M/SD lookup). Use now if you just want "current".
   *
   * Returns null if the data isn't loaded ([loadIfNeeded] must have completed),
   * the baby's age is outside the CDC tables, or sex is unknown.
   */
  fun calcPercentile(
    sex: String?,
    birthDateMillis: Long,
    value: Double,
    type: MeasurementType,
    entryTimestampMillis: Long,
  ): Int? {
    val data = selectMeasurementData(sex, type) ?: return null
    val daysSinceBirth = ((entryTimestampMillis - birthDateMillis) / MS_PER_DAY).toInt()
    if (daysSinceBirth < 0) return null

    val row = interpolateMeasurementRow(data, daysSinceBirth) ?: return null
    if (row.sd == 0.0) return null
    val z = (value - row.m) / row.sd
    return percentileFromZScore(z)
  }

  private fun selectMeasurementData(sex: String?, type: MeasurementType): List<MeasurementRow>? =
    when (sex?.lowercase(Locale.ROOT)) {
      "male" -> if (type == MeasurementType.WEIGHT) boyWeightMeasurements else boyLengthMeasurements
      "female" -> if (type == MeasurementType.WEIGHT) girlWeightMeasurements else girlLengthMeasurements
      else -> null
    }

  /**
   * Finds CSV row(s) bracketing the target age-in-days and returns an M/SD pair for
   * that exact day — interpolating linearly between two rows when both are in range.
   * Matches the iOS ±7-day window behaviour.
   */
  private fun interpolateMeasurementRow(
    data: List<MeasurementRow>,
    daysSinceBirth: Int,
  ): MeasurementRow? {
    val windowLow = daysSinceBirth - DATA_RESOLUTION_DAYS + 1
    val windowHigh = daysSinceBirth + DATA_RESOLUTION_DAYS
    val matching = data.filter { it.day in windowLow..windowHigh }
    return when {
      matching.size >= 2 -> {
        val first = matching[0]
        val second = matching[1]
        val weight = (daysSinceBirth - first.day).toDouble() / DATA_RESOLUTION_DAYS
        MeasurementRow(
          day = daysSinceBirth,
          m = lerp(first.m, second.m, weight),
          sd = lerp(first.sd, second.sd, weight),
        )
      }
      matching.size == 1 -> matching[0]
      else -> null
    }
  }

  private fun lerp(a: Double, b: Double, weight: Double): Double = a + (b - a) * weight

  /**
   * Converts a z-score to an integer percentile 0–100 using [BabyZTable].
   * Mirrors `PercentileService.checkZScoreAgainstZTable` — positive z's are looked
   * up by their negative mirror and flipped with `1 − Φ(-z)`.
   */
  /**
   * Formats a percentile (0–100) as a display number — `"10"` for normal values,
   * `"< 1"` for sub-1st-percentile, `"> 99"` for above-99th. Returns null for null
   * or negative input, and for the special `"private"` sex value (matches iOS
   * `PercentileService.getPercentileSuffix` semantics).
   *
   * The `%` (or any other unit) is left to the renderer — this returns the number
   * portion only, so the UI can style the unit separately (heading2 + subHeading1).
   */
  fun formatPercentileNumber(percentile: Int?, sex: String? = null): String? {
    if (percentile == null || percentile < 0) return null
    if (sex?.lowercase(Locale.ROOT) == "private") return null
    return when {
      percentile < 1 -> "< 1"
      percentile > 99 -> "> 99"
      else -> percentile.toString()
    }
  }

  private fun percentileFromZScore(z: Double): Int {
    if (z > 3.49) return 100
    if (z < -3.49) return 0

    // Truncate toward zero to 2 decimals — matches iOS floor/ceil logic.
    val zRounded: Double = if (z > 0.0) floor(z * 100.0) / 100.0 else ceil(z * 100.0) / 100.0

    // Tenths-digit row key — always negative or zero so we can reuse the stored table.
    val tenths: Double = if (z > 0.0) -(floor(zRounded * 10.0) / 10.0) else ceil(zRounded * 10.0) / 10.0
    val rowKey = if (tenths == 0.0) "0.0" else String.format(Locale.ROOT, "%.1f", tenths)

    // Hundredths digit in [0, 9]. Column order is [0.09, 0.08, …, 0.00], so digit H → col 9-H.
    val hundredthsDigit: Int = if (tenths == 0.0) {
      (abs(zRounded) * 100.0).roundToInt().coerceIn(0, 9)
    } else {
      (abs(zRounded % tenths) * 100.0).roundToInt().coerceIn(0, 9)
    }

    val row = BabyZTable.rows[rowKey] ?: return -1
    val col = 9 - hundredthsDigit
    val tableValue = row[col]

    val basePercent = if (z > 0.0) 1.0 - tableValue else tableValue
    return (basePercent * 100.0).roundToInt().coerceIn(0, 100)
  }
}
