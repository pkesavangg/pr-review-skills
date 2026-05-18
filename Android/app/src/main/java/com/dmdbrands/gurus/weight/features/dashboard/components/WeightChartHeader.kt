package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.ChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Chart header for the Weight product — average weight value + unit (lb / lbs / kg).
 * Subtracts the weightless offset when weightless mode is on, mirroring
 * `ProductChart.kt`'s default unit handling so header text stays consistent
 * with the chart's axis labels.
 */
@Composable
fun WeightChartHeader(
  state: BaseDashboardState,
  segment: GraphSegment,
) {
  val segmentState = state.forSegment(segment)
  val rangeText = formatRangeText(segmentState, segment)

  val weightState = state as? WeightDashboardState
  val target = segmentState.target.filterIsInstance<PeriodBodyScaleSummary>()
  val avgLb = if (target.isEmpty()) 0.0 else target.map { it.weight }.average()
  val weightUnit = weightState?.weightUnit ?: WeightUnit.LB
  val weightless = weightState?.weightless
  val weightlessOffset = if (weightless?.isWeightlessOn == true) {
    weightless.weightlessWeight.toDouble()
  } else {
    0.0
  }
  val displayValue = convertWeight(avgLb - weightlessOffset, WeightUnit.LB, weightUnit)
  val label = if (target.isEmpty()) "000.0" else formatWeightValue(displayValue)
  val displayUnit = remember(weightUnit, displayValue) { displayUnit(weightUnit, displayValue) }

  ChartHeader(
    rangeData = rangeText,
    markerIndex = state.markerIndex,
  ) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(text = label, style = MeTheme.typography.heading2, color = MeTheme.colorScheme.textBody)
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = displayUnit,
        style = MeTheme.typography.subHeading2,
        color = MeTheme.colorScheme.textSubheading,
        modifier = Modifier.offset(y = (-10).dp),
      )
    }
  }
}

private fun displayUnit(weightUnit: WeightUnit, weight: Double): String = when (weightUnit) {
  WeightUnit.KG -> DashboardSnapshotStrings.Kg
  WeightUnit.LB, WeightUnit.LB_OZ -> if (weight <= 1.0 && weight != 0.0) DashboardSnapshotStrings.Lb else DashboardSnapshotStrings.Lbs
}

/**
 * Shared "Jun 29 - Jul 5, 2025" range text derivation. Lives here so each per-product
 * header file is self-contained — same snippet duplicated across the three is cheaper
 * than threading a shared helper for one line.
 */
internal fun formatRangeText(
  segmentState: com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState,
  segment: GraphSegment,
): String = (segmentState.visibleMin ?: segmentState.chartMinX?.toLong())?.let { min ->
  (segmentState.visibleMax ?: segmentState.chartMaxX?.toLong())?.let { max ->
    GraphUtil.formatDateRange(min, max, segment)
  }
} ?: ""
