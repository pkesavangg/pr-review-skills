package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.R
import java.io.BufferedReader
import java.io.InputStreamReader
import android.content.Context

/**
 * WHO weight-for-age and length-for-age percentile lookup from CSV data.
 * CSV format: Day,5th,10th,25th,50th,75th,90th,95th.
 * Weight values in decigrams, length values in mm (tenths of mm).
 * Rows are every 7 days from birth to ~18 years.
 */
object BabyPercentileHelper {

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

  private var boyWeightData: List<PercentileRow>? = null
  private var girlWeightData: List<PercentileRow>? = null
  private var boyLengthData: List<PercentileRow>? = null
  private var girlLengthData: List<PercentileRow>? = null

  @Synchronized
  fun loadIfNeeded(context: Context) {
    if (boyWeightData == null) boyWeightData = parseCsv(context, R.raw.boy_weight_percentiles)
    if (girlWeightData == null) girlWeightData = parseCsv(context, R.raw.girl_weight_percentiles)
    if (boyLengthData == null) boyLengthData = parseCsv(context, R.raw.boy_length_percentiles)
    if (girlLengthData == null) girlLengthData = parseCsv(context, R.raw.girl_length_percentiles)
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
}
