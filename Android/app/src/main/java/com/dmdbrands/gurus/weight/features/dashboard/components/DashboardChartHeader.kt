package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent

/**
 * Thin dispatcher that picks the per-product chart header. Each header lives in its
 * own file and owns its product-specific logic + sheet triggers:
 *
 * - [WeightChartHeader] — value + lb/kg unit
 * - [BpChartHeader] — mmHg/pulse + AHA ratings sheet
 * - [BabyChartHeader] — value + W/H toggle + CDC percentiles sheet
 */
@Composable
fun DashboardChartHeader(
  state: BaseDashboardState,
  segment: GraphSegment,
  product: ProductSelection,
  handleIntent: ((BaseGraphIntent) -> Unit)? = null,
) {
  when (product) {
    is ProductSelection.MyWeight -> WeightChartHeader(state = state, segment = segment)
    is ProductSelection.BloodPressure -> BpChartHeader(state = state, segment = segment)
    is ProductSelection.Baby -> BabyChartHeader(state = state, segment = segment, handleIntent = handleIntent)
  }
}
