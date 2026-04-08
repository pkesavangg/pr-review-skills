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
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
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
      val weightState = state as? WeightDashboardState
      val target = weightState?.data ?: emptyList()
      val avg = if (target.isEmpty()) 0.0 else target.map { it.weight }.average()
      val label = if (target.isEmpty()) "000.0" else formatWeightValue(avg)
      WeightChartHeader(
        segmentState = segmentState,
        segment = segment,
        weightUnit = weightState?.weightUnit ?: com.dmdbrands.gurus.weight.domain.model.common.WeightUnit.KG,
        weightData = label,
        rangeData = rangeText,
        weightValue = avg,
      )
    }
    is ProductSelection.BloodPressure -> {
      // BP header reads from segment target — will be improved when BP has its own data type
      BpChartHeader(
        segmentState = segmentState,
        segment = segment,
        systolic = null, // TODO: read from BpDashboardState
        diastolic = null,
        pulse = null,
        rangeData = rangeText,
      )
    }
    is ProductSelection.Baby -> {
      val avg = 0.0 // TODO: read from BabyDashboardState
      WeightChartHeader(
        segmentState = segmentState,
        segment = segment,
        weightData = "000.0",
        rangeData = rangeText,
        weightValue = avg,
      )
    }
  }
}
