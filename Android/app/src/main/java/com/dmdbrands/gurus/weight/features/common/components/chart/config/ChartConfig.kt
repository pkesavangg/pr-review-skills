package com.dmdbrands.gurus.weight.features.common.components.chart.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.theme.MeTheme

data class LineSpec(
  val color: Color,
)

data class ChartConfig(
  val lines: List<LineSpec>,
  val goal: Goal? = null,
  val goalWeight: Double? = null,
  val isWeightlessMode: Boolean = false,
  val hasSecondaryLayer: Boolean = false,
  val useAllSeriesForYRange: Boolean = false,
  val secondaryLineColor: Color? = null,
  val hasPercentileLayer: Boolean = false,
  val percentileBandColor: Color? = null,
)

@Composable
fun rememberChartConfig(
  product: ProductSelection,
  goal: Goal? = null,
  avgSystolic: Int? = null,
  avgDiastolic: Int? = null,
  avgPulse: Int? = null,
  hasPercentile: Boolean = false,
): ChartConfig {
  val primaryColor = MeTheme.colorScheme.primaryAction
  val secondaryColor = MeTheme.colorScheme.secondaryAction
  return remember(product, goal, avgSystolic, avgDiastolic, avgPulse, hasPercentile) {
    when (product) {
      is ProductSelection.MyWeight -> ChartConfig(
        lines = listOf(LineSpec(color = primaryColor)),
        goal = goal,
        goalWeight = goal?.goalWeight,
        isWeightlessMode = goal?.account?.isWeightlessOn ?: false,
        hasSecondaryLayer = true,
        useAllSeriesForYRange = false,
        secondaryLineColor = secondaryColor,
      )

      is ProductSelection.BloodPressure -> ChartConfig(
        lines = listOf(
          LineSpec(color = avgSystolic?.let { SnapshotColors.systolicColor(it) } ?: SnapshotColors.BloodPressure),
          LineSpec(color = avgDiastolic?.let { SnapshotColors.diastolicColor(it) } ?: Color(0xFF7B726E)),
          LineSpec(color = avgPulse?.let { SnapshotColors.pulseColor(it) } ?: Color(0xFF00B3E3)),
        ),
        useAllSeriesForYRange = true,
      )

      is ProductSelection.Baby -> ChartConfig(
        lines = listOf(LineSpec(color = SnapshotColors.Baby)),
        hasPercentileLayer = hasPercentile,
        percentileBandColor = SnapshotColors.PercentileBand,
        // Baby avgWeightDecigrams is raw storage (not display units) — skip pre-warming.
      )
    }
  }
}
