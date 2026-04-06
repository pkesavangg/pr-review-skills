package com.dmdbrands.gurus.weight.features.common.helper

import android.content.Context
import com.dmdbrands.gurus.weight.R
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WHO weight-for-age percentile lookup from CSV data.
 * CSV format: Day,5th,10th,25th,50th,75th,90th,95th (values in decigrams).
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

    private var boyData: List<PercentileRow>? = null
    private var girlData: List<PercentileRow>? = null

    fun loadIfNeeded(context: Context) {
        if (boyData == null) boyData = parseCsv(context, R.raw.boy_weight_percentiles)
        if (girlData == null) girlData = parseCsv(context, R.raw.girl_weight_percentiles)
    }

    /**
     * Returns interpolated percentile values for given days since birth.
     * @param sex "male" or "female"
     * @param daysSinceBirth list of days to look up
     * @return map of percentile key ("p50", "p5", etc.) to list of values in decigrams
     */
    fun getPercentileLines(
        sex: String?,
        daysSinceBirth: List<Int>,
    ): Map<String, List<Double>> {
        val data = when (sex?.lowercase()) {
            "male" -> boyData
            "female" -> girlData
            else -> null
        } ?: return emptyMap()

        val keys = listOf("p5", "p50", "p95")
        val result = keys.associateWith { mutableListOf<Double>() }

        for (day in daysSinceBirth) {
            val row = interpolateRow(data, day)
            if (row != null) {
                result["p5"]?.add(row.p5)
                result["p50"]?.add(row.p50)
                result["p95"]?.add(row.p95)
            }
        }

        return result
    }

    private fun interpolateRow(data: List<PercentileRow>, day: Int): PercentileRow? {
        if (data.isEmpty()) return null
        if (day <= data.first().day) return data.first()
        if (day >= data.last().day) return data.last()

        val idx = data.indexOfLast { it.day <= day }
        if (idx < 0) return data.first()
        if (idx >= data.lastIndex) return data.last()

        val a = data[idx]
        val b = data[idx + 1]
        val t = (day - a.day).toDouble() / (b.day - a.day).toDouble()

        return PercentileRow(
            day = day,
            p5 = a.p5 + t * (b.p5 - a.p5),
            p10 = a.p10 + t * (b.p10 - a.p10),
            p25 = a.p25 + t * (b.p25 - a.p25),
            p50 = a.p50 + t * (b.p50 - a.p50),
            p75 = a.p75 + t * (b.p75 - a.p75),
            p90 = a.p90 + t * (b.p90 - a.p90),
            p95 = a.p95 + t * (b.p95 - a.p95),
        )
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
