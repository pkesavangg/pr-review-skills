package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * UI state for the graph component, holding all chart-related state variables.
 *
 * @property point Current pointer point for marker interaction.
 * @property timeStamp List of timestamps for the graph.
 * @property minTarget Minimum target timestamp for the visible range.
 * @property maxTarget Maximum target timestamp for the visible range.
 * @property minYTarget Minimum Y-axis target value.
 * @property secondaryMinYTarget Secondary Y-axis minimum target value.
 * @property maxYTarget Maximum Y-axis target value.
 * @property secondaryMaxYTarget Secondary Y-axis maximum target value.
 * @property selectedTarget Selected target timestamp.
 * @property markerIndex Index of the current marker.
 * @property isUpdating Whether the graph is currently updating.
 * @property modelProducer Chart model producer for Vico library.
 * @property graphKey Hash code of the graph lines for change detection.
 * @property computationJob Current computation job for async operations.
 * @property stepSize Step size for Y-axis scaling.
 * @property scrollState Current scroll state of the chart.
 * @property initialTimeStamp Initial timestamp for the graph.
 * @property todayMills Current timestamp in milliseconds.
 * @property startRangeX Start range for X-axis.
 * @property endRangeX End range for X-axis.
 * @property separators Period separators for the graph.
 * @property isEmpty Whether the graph has no data.
 * @property isSingleWindow Whether the current visible range lies within a single segment window.
 * @property segment Current graph segment (WEEK, MONTH, etc.).
 * @property goal Current goal for reference.
 * @property graphLines Primary graph lines data.
 * @property secondaryGraphLines Secondary graph lines data.
 */
@Stable
data class GraphState(
  val weightUnit: WeightUnit = WeightUnit.KG,
  val data: ImmutableList<PeriodBodyScaleSummary> = persistentListOf(),
  val target: ImmutableList<PeriodBodyScaleSummary> = persistentListOf(),
  val secondaryKey: DashboardKey? = null,
  val primaryYAxis: CartesianRangeValues? = null,
  val secondaryYAxis: CartesianRangeValues? = null,
  val primaryYStep: Double? = null,
  val goal: Goal? = null,
  val isEmptyGraph: Boolean = false,
  val modelProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  val minTarget: Long? = null,
  val maxTarget: Long? = null,
  val markerIndex: Double? = null,
  val isUpdating: Boolean = false,
  val isLoading: Boolean = false,
  val isSingleWindow: Boolean = false,
) : IReducer.State {
  val graphKey: Int = data.hashCode()
  val graphLines: List<GraphLine> = listOf(this.data.getWeightGraphPoints())
  val secondaryGraphLines: GraphLine? = secondaryKey?.let { data.toGraphPoints((it as DashboardKey.Metric).key) }

  fun getStartTimestamp(): Long {
    return this.data.minByOrNull { it.getTimeStamp() }?.getTimeStamp() ?: Calendar.getInstance().timeInMillis
  }

  fun getEndTimestamp(): Long {
    return this.data.maxByOrNull { it.getTimeStamp() }?.getTimeStamp() ?: Calendar.getInstance().timeInMillis
  }

  fun createFallBackData(
    segment: GraphSegment,
    timeStamps: List<Long>? = null,
    fallbackValues: List<List<Double>>? = null
  ): List<PeriodBodyScaleSummary> {
    if (timeStamps == null && this.markerIndex == null) return emptyList()
    val filteringTimeStamp = timeStamps ?: listOf(this.markerIndex?.toLong())
    return filteringTimeStamp.mapIndexedNotNull { index, it ->
      if (it == null || fallbackValues?.isEmpty() == true) return@mapIndexedNotNull null
      PeriodBodyScaleSummary(
        period = timestampToPeriodString(it, segment),
        entryTimestamp = DateTimeConverter.timestampToIso(it),
        weight = fallbackValues?.firstOrNull()?.get(index) ?: 0.0,
        unit = weightUnit,
      )
    }
  }

  /**
   * Converts a timestamp to a period string based on the graph segment.
   *
   * @param timestamp The timestamp in milliseconds to convert.
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @return Formatted period string: "YYYY-MM-DD" for WEEK/MONTH segments, "YYYY-MM" for YEAR/TOTAL segments.
   */
  private fun timestampToPeriodString(timestamp: Long, segment: GraphSegment): String {
    val localZone = ZoneId.systemDefault()
    val localDateTime = Instant.ofEpochMilli(timestamp).atZone(localZone)

    return when (segment) {
      GraphSegment.WEEK, GraphSegment.MONTH -> {
        // Format as "YYYY-MM-DD" for day-level precision
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      }

      GraphSegment.YEAR, GraphSegment.TOTAL -> {
        // Format as "YYYY-MM" for month-level precision
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
      }
    }
  }
}

/**
 * Gets the weight graph points for the given segment.
 * Optimized to avoid repeated sorting and processing.
 */
fun List<PeriodBodyScaleSummary>.getWeightGraphPoints(): GraphLine {
  val entries = this
  // Sort only if entries are not already sorted
  val sortedEntries = if (entries.size <= 1) {
    entries
  } else {
    // Check if already sorted to avoid unnecessary sorting
    val isSorted = entries.zipWithNext().all { (a, b) -> a.entryTimestamp <= b.entryTimestamp }
    if (isSorted) entries else entries.sortedBy { it.entryTimestamp }
  }
  return sortedEntries.toWeightGraphPoints()
}



