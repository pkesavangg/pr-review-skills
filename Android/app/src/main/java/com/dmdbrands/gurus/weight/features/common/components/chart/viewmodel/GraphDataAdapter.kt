package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary

/**
 * A single line series: parallel x/y value lists.
 */
data class SeriesData(
  val xValues: List<Long>,
  val yValues: List<Double>,
)

/**
 * Converts product-specific [GraphData] into line series and target data
 * that [GraphViewModel] can feed into the Vico model producer.
 * All series go into a single `lineSeries { }` block (one Vico layer).
 */
interface GraphDataAdapter {
  /** Convert [GraphData] into x/y pairs per line series. */
  fun toLineSeries(graphData: GraphData): List<SeriesData>

  /** Convert [GraphData] into [PeriodSummary] for target/fallback display. */
  fun toTargetData(graphData: GraphData): List<PeriodSummary>

  /** Extract timestamps from [GraphData] for range computation. */
  fun getTimestamps(graphData: GraphData): List<Long>

  companion object {
    fun forProduct(product: ProductSelection): GraphDataAdapter = when (product) {
      is ProductSelection.MyWeight -> WeightGraphDataAdapter()
      is ProductSelection.BloodPressure -> BpGraphDataAdapter()
      is ProductSelection.Baby -> BabyGraphDataAdapter()
    }
  }
}

/**
 * Weight adapter: 1 series (weight values).
 */
class WeightGraphDataAdapter : GraphDataAdapter {

  override fun toLineSeries(graphData: GraphData): List<SeriesData> {
    val entries = (graphData as? GraphData.Weight)?.data ?: return emptyList()
    val sorted = entries.sortedBy { it.getTimeStamp() }
    val pairs = sorted.mapNotNull { entry ->
      val ts = entry.getTimeStamp()
      val w = entry.weight
      if (w.isFinite()) ts to w else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  override fun toTargetData(graphData: GraphData): List<PeriodSummary> {
    return (graphData as? GraphData.Weight)?.data ?: emptyList()
  }

  override fun getTimestamps(graphData: GraphData): List<Long> {
    return (graphData as? GraphData.Weight)?.data?.map { it.getTimeStamp() } ?: emptyList()
  }
}

/**
 * BP adapter: 3 series in one layer (systolic, diastolic, pulse).
 * ScrollAwareRangeProvider.buildCache merges all series' Y values
 * so the Y range spans all 3 lines.
 */
class BpGraphDataAdapter : GraphDataAdapter {

  override fun toLineSeries(graphData: GraphData): List<SeriesData> {
    val entries = (graphData as? GraphData.BloodPressure)?.data ?: return emptyList()
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    if (sorted.isEmpty()) return emptyList()
    val timestamps = sorted.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    return listOf(
      SeriesData(timestamps, sorted.map { it.avgSystolic.toDouble() }),
      SeriesData(timestamps, sorted.map { it.avgDiastolic.toDouble() }),
      SeriesData(timestamps, sorted.map { it.avgPulse.toDouble() }),
    )
  }

  override fun toTargetData(graphData: GraphData): List<PeriodSummary> {
    return (graphData as? GraphData.BloodPressure)?.data ?: emptyList()
  }

  override fun getTimestamps(graphData: GraphData): List<Long> {
    return (graphData as? GraphData.BloodPressure)?.data
      ?.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) } ?: emptyList()
  }
}

/**
 * Baby weight adapter: 1 series (weight in decigrams → lbs).
 */
class BabyGraphDataAdapter : GraphDataAdapter {

  override fun toLineSeries(graphData: GraphData): List<SeriesData> {
    val entries = (graphData as? GraphData.Baby)?.data ?: return emptyList()
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    val pairs = sorted.mapNotNull { entry ->
      val ts = DateTimeConverter.isoToTimestamp(entry.entryTimestamp)
      val weight = entry.avgWeightDecigrams?.let { it / 283.495 / 16.0 }
      if (weight != null) ts to weight else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  override fun toTargetData(graphData: GraphData): List<PeriodSummary> {
    return (graphData as? GraphData.Baby)?.data ?: emptyList()
  }

  override fun getTimestamps(graphData: GraphData): List<Long> {
    return (graphData as? GraphData.Baby)?.data
      ?.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) } ?: emptyList()
  }
}

/**
 * Baby height adapter: 1 series (length in mm → inches).
 */
class BabyHeightGraphDataAdapter : GraphDataAdapter {

  override fun toLineSeries(graphData: GraphData): List<SeriesData> {
    val entries = (graphData as? GraphData.Baby)?.data ?: return emptyList()
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    val pairs = sorted.mapNotNull { entry ->
      val ts = DateTimeConverter.isoToTimestamp(entry.entryTimestamp)
      val length = entry.avgLengthMillimeters?.let { it / 25.4 }
      if (length != null) ts to length else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  override fun toTargetData(graphData: GraphData): List<PeriodSummary> {
    return (graphData as? GraphData.Baby)?.data ?: emptyList()
  }

  override fun getTimestamps(graphData: GraphData): List<Long> {
    return (graphData as? GraphData.Baby)?.data
      ?.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) } ?: emptyList()
  }
}
