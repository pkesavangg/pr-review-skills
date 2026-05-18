package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphLabelHelper
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Returns the appropriate unit label based on the weight value and unit.
 * For LB: returns "lb" if weight <= 1.0, "lbs" if weight > 1.0
 * For KG: always returns "kg"
 * Special case: When weight is 0.0 (no data), returns "lbs" for LB unit to show plural form
 *
 * @param weightUnit The weight unit enum (KG or LB)
 * @param weight The weight value to determine the appropriate unit label
 * @return The formatted unit string
 */
private fun getDisplayUnit(weightUnit: WeightUnit, weight: Double): String {
  return when (weightUnit) {
    WeightUnit.KG -> "kg"
    WeightUnit.LB -> when {
      weight == 0.0 -> "lbs" // Default to plural when no data
      weight <= 1.0 -> "lb"
      else -> "lbs"
    }
  }
}

@Composable
fun ChartHeader(
  state: GraphState,
  segment: GraphSegment,
  weightData: String,
  rangeData: String,
  weightValue: Double,
) {
  val weightUnit = state.weightUnit
  val displayUnitText = remember(weightUnit, weightValue) {
    getDisplayUnit(weightUnit, weightValue)
  }
  
  val hasSelection = state.markerIndex != null
  // Per MA-3965: on Week/Month the most-recent day shows "latest entry"; every other
  // day shows "day average". "Latest day" is the highest entry timestamp present in
  // the current data set, not today's calendar date.
  val latestDayTimestamp = remember(state.data) {
    state.data.maxOfOrNull { it.getTimeStamp() }
  }
  val isLatestDaySelected = remember(state.markerIndex, latestDayTimestamp) {
    val marker = state.markerIndex?.toLong()
    marker != null && latestDayTimestamp != null && marker == latestDayTimestamp
  }
  val labelText = when {
    state.isEmptyGraph -> "no entries"
    else -> GraphLabelHelper.selectionLabel(segment, hasSelection, isLatestDaySelected)
  }

  Column(
    modifier = Modifier.padding(
      horizontal = MeTheme.spacing.sm,
      vertical = MeTheme.spacing.x3s,
    ),
  ) {
    Text(
      text = labelText,
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
    )

    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        text = weightData.ifBlank { "000.0" },
        style = MeTheme.typography.heading2,
        color = MeTheme.colorScheme.textBody,
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = displayUnitText,
        style = MeTheme.typography.subHeading2,
        color = MeTheme.colorScheme.textSubheading,
        modifier = Modifier.offset(y = (-10).dp),
      )
    }

    Text(
      text = rangeData.lowercase(),
      style = MeTheme.typography.subHeading2,
      color = if (state.markerIndex == null) MeTheme.colorScheme.textSubheading else Color.Transparent,
    )
  }
}
