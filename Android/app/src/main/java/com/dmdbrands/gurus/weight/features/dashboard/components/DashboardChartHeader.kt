package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.chart.bp.BpChartHeader
import com.dmdbrands.gurus.weight.features.common.components.chart.weight.WeightChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue

/**
 * Product-aware chart header. Reads VM state internally.
 */
@Composable
fun <S : BaseDashboardState> DashboardChartHeader(
  viewModel: BaseDashboardViewModel<S, *>,
  segment: GraphSegment,
  product: ProductSelection,
) {
  val state by viewModel.state.collectAsState()
  val segmentState = state.forSegment(segment)
  val rangeText = segmentState.minTarget?.let { min ->
    segmentState.maxTarget?.let { max -> GraphUtil.formatDateRange(min, max, segment) }
  } ?: ""

  when (product) {
    is ProductSelection.MyWeight -> {
      val avg = if (segmentState.target.isEmpty()) 0.0 else segmentState.target.map { it.weight }.average()
      val label = if (segmentState.target.isEmpty()) "000.0" else formatWeightValue(avg)
      WeightChartHeader(
        state = state,
        segmentState = segmentState,
        segment = segment,
        weightData = label,
        rangeData = rangeText,
        weightValue = avg,
      )
    }
    is ProductSelection.BloodPressure -> {
      val target = segmentState.target
      val avgSys = target.map { it.weight.toInt() }.takeIf { it.isNotEmpty() }?.average()?.toInt()
      val avgDia = target.map { it.bodyFat?.toInt() ?: 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
      val avgPulse = target.map { it.pulse?.toInt() ?: 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
      BpChartHeader(
        state = state,
        segmentState = segmentState,
        segment = segment,
        systolic = avgSys,
        diastolic = avgDia,
        pulse = avgPulse,
        rangeData = rangeText,
      )
    }
    is ProductSelection.Baby -> {
      val avg = if (segmentState.target.isEmpty()) 0.0 else segmentState.target.map { it.weight }.average()
      val label = if (segmentState.target.isEmpty()) "000.0" else formatWeightValue(avg)
      WeightChartHeader(
        state = state,
        segmentState = segmentState,
        segment = segment,
        weightData = label,
        rangeData = rangeText,
        weightValue = avg,
      )
    }
  }
}
