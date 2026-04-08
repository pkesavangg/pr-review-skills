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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.chart.ChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.theme.MeTheme

private fun getDisplayUnit(weightUnit: WeightUnit, weight: Double): String = when (weightUnit) {
  WeightUnit.KG -> "kg"
  WeightUnit.LB -> if (weight <= 1.0 && weight != 0.0) "lb" else "lbs"
}

/**
 * Product-aware chart header. Uses shared [ChartHeader] for Row 1 + Row 3,
 * provides product-specific value display as the slot.
 * Pure display — receives state, no VM reference.
 */
@Composable
fun DashboardChartHeader(
  state: BaseDashboardState,
  segment: GraphSegment,
  product: ProductSelection,
) {
  val segmentState = state.forSegment(segment)
  val rangeText = segmentState.minTarget?.let { min ->
    segmentState.maxTarget?.let { max -> GraphUtil.formatDateRange(min, max, segment) }
  } ?: ""

  ChartHeader(
    segmentState = segmentState,
    segment = segment,
    rangeData = rangeText,
    markerIndex = state.markerIndex,
  ) {
    when (product) {
      is ProductSelection.MyWeight -> {
        val weightState = state as? WeightDashboardState
        val target = segmentState.target
        val avg = if (target.isEmpty()) 0.0 else target.map { it.weight }.average()
        val label = if (target.isEmpty()) "000.0" else formatWeightValue(avg)
        val weightUnit = weightState?.weightUnit ?: WeightUnit.KG
        val displayUnit = remember(weightUnit, avg) { getDisplayUnit(weightUnit, avg) }

        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = label,
            style = MeTheme.typography.heading2,
            color = MeTheme.colorScheme.textBody,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = displayUnit,
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.offset(y = (-10).dp),
          )
        }
      }

      is ProductSelection.BloodPressure -> {
        val target = segmentState.target
        val avgSys = target.map { it.weight.toInt() }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgDia = target.map { it.bodyFat?.toInt() ?: 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgPulse = target.map { it.pulse?.toInt() ?: 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()

        Row {
          Text(text = "mmhg", style = MeTheme.typography.subHeading1, color = MeTheme.colorScheme.textSubheading)
          Spacer(modifier = Modifier.weight(1f))
          Text(text = "pulse", style = MeTheme.typography.subHeading1, color = MeTheme.colorScheme.textSubheading)
        }
        Row(verticalAlignment = Alignment.Bottom) {
          if (avgSys != null && avgDia != null) {
            Text(
              text = buildAnnotatedString {
                withStyle(SpanStyle(color = SnapshotColors.systolicColor(avgSys))) { append("$avgSys") }
                withStyle(SpanStyle(color = MeTheme.colorScheme.textSubheading)) { append("/") }
                withStyle(SpanStyle(color = SnapshotColors.diastolicColor(avgDia))) { append("$avgDia") }
              },
              style = MeTheme.typography.heading2,
            )
          } else {
            Text(text = "—", style = MeTheme.typography.heading2, color = SnapshotColors.BloodPressure)
          }
          Spacer(modifier = Modifier.weight(1f))
          Text(
            text = avgPulse?.toString() ?: "—",
            style = MeTheme.typography.heading2,
            color = if (avgPulse != null) SnapshotColors.pulseColor(avgPulse) else MeTheme.colorScheme.textSubheading,
          )
        }
      }

      is ProductSelection.Baby -> {
        val target = segmentState.target
        val avg = if (target.isEmpty()) 0.0 else target.map { it.weight }.average()
        val label = if (target.isEmpty()) "000.0" else formatWeightValue(avg)

        Row(verticalAlignment = Alignment.Bottom) {
          Text(text = label, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "lbs",
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.offset(y = (-10).dp),
          )
        }
      }
    }
  }
}
