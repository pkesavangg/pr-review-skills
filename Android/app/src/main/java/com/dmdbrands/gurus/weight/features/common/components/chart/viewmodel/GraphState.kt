package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.dmdbrands.gurus.weight.features.common.components.chart.CartesianRangeValues
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Per-product graph state. Each product gets its own instance with
 * a stable [modelProducer] that lives for the VM's lifetime.
 */
@Stable
data class ProductGraphState(
  val data: ImmutableList<PeriodBodyScaleSummary> = persistentListOf(),
  val target: ImmutableList<PeriodBodyScaleSummary> = persistentListOf(),
  val minTarget: Long? = null,
  val maxTarget: Long? = null,
  val chartMinX: Double? = null,
  val chartMaxX: Double? = null,
  val markerIndex: Double? = null,
  val isEmptyGraph: Boolean = false,
  val isSingleWindow: Boolean = false,
  val modelProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
) {
  fun getStartTimestamp(): Long {
    return data.minByOrNull { it.getTimeStamp() }?.getTimeStamp() ?: Calendar.getInstance().timeInMillis
  }

  fun getEndTimestamp(): Long {
    return data.maxByOrNull { it.getTimeStamp() }?.getTimeStamp() ?: Calendar.getInstance().timeInMillis
  }

  val graphLines: List<GraphLine> = listOf(data.getWeightGraphPoints())
}

/**
 * UI state for the graph component. Holds shared fields and a per-product map.
 * Product entries are added lazily when new products become available.
 */
@Stable
data class GraphState(
  val weightUnit: WeightUnit = WeightUnit.KG,
  val secondaryKey: DashboardKey? = null,
  val goal: Goal? = null,
  val primaryYAxis: CartesianRangeValues? = null,
  val primaryYStep: Double? = null,
  val isUpdating: Boolean = false,
  val isLoading: Boolean = false,
  /** Per-product state map. Entries added lazily when products appear. */
  val productStates: Map<ProductType, ProductGraphState> = emptyMap(),
) : IReducer.State {

  /** Get state for a product, or a default empty state. */
  fun forProduct(productType: ProductType): ProductGraphState {
    return productStates[productType] ?: ProductGraphState()
  }

  fun createFallBackData(
    productType: ProductType,
    segment: GraphSegment,
    timeStamps: List<Long>? = null,
    fallbackValues: List<List<Double>>? = null,
  ): List<PeriodBodyScaleSummary> {
    val ps = forProduct(productType)
    if (timeStamps == null && ps.markerIndex == null) return emptyList()
    val filteringTimeStamp = timeStamps ?: listOf(ps.markerIndex?.toLong())
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

  private fun timestampToPeriodString(timestamp: Long, segment: GraphSegment): String {
    val localZone = ZoneId.systemDefault()
    val localDateTime = Instant.ofEpochMilli(timestamp).atZone(localZone)
    return when (segment) {
      GraphSegment.WEEK, GraphSegment.MONTH ->
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      GraphSegment.YEAR, GraphSegment.TOTAL ->
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }
  }
}

/**
 * Gets the weight graph points for the given segment.
 * Optimized to avoid repeated sorting and processing.
 */
fun List<PeriodBodyScaleSummary>.getWeightGraphPoints(): GraphLine {
  val entries = this
  val sortedEntries = if (entries.size <= 1) {
    entries
  } else {
    val isSorted = entries.zipWithNext().all { (a, b) -> a.entryTimestamp <= b.entryTimestamp }
    if (isSorted) entries else entries.sortedBy { it.entryTimestamp }
  }
  return sortedEntries.toWeightGraphPoints()
}
