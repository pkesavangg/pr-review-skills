package com.dmdbrands.gurus.weight.features.common.components.chart.weight

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
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.ProductGraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.theme.MeTheme

private fun getDisplayUnit(weightUnit: WeightUnit, weight: Double): String {
  return when (weightUnit) {
    WeightUnit.KG -> "kg"
    WeightUnit.LB -> when {
      weight == 0.0 -> "lbs"
      weight <= 1.0 -> "lb"
      else -> "lbs"
    }
  }
}

@Composable
fun WeightChartHeader(
  state: GraphState,
  productState: ProductGraphState = ProductGraphState(),
  segment: GraphSegment,
  weightData: String,
  rangeData: String,
  weightValue: Double,
) {
  val weightUnit = state.weightUnit
  val displayUnitText = remember(weightUnit, weightValue) {
    getDisplayUnit(weightUnit, weightValue)
  }

  val headerText =
    if (productState.markerIndex != null) {
      when (segment) {
        GraphSegment.WEEK, GraphSegment.MONTH -> "day"
        else -> "month"
      }
    } else
      segment.name.lowercase()

  Column(
    modifier = Modifier.padding(
      horizontal = MeTheme.spacing.sm,
      vertical = MeTheme.spacing.x3s,
    ),
  ) {
    Text(
      text = if (productState.isEmptyGraph) "no entries" else "$headerText average",
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
      color = if (productState.markerIndex == null) MeTheme.colorScheme.textSubheading else Color.Transparent,
    )
  }
}
