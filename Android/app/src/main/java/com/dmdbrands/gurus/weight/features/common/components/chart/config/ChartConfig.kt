package com.dmdbrands.gurus.weight.features.common.components.chart.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Specification for a single chart line.
 */
data class LineSpec(
  val color: Color,
)

/**
 * Declarative configuration for building a product-specific chart.
 * Built via [rememberChartConfig] inside a composable to resolve theme colors.
 *
 * @param lines Line specs per series (1 for weight, 3 for BP, 1 for baby).
 * @param goalWeight Non-null if goal marker should be shown (weight only).
 * @param isWeightlessMode Whether weightless mode affects the Y range.
 * @param hasSecondaryLayer Whether a secondary metric overlay layer exists (weight only).
 * @param useAllSeriesForYRange If true, Y range spans all series (BP). If false, uses first series only.
 */
data class ChartConfig(
  val lines: List<LineSpec>,
  val goalWeight: Double? = null,
  val isWeightlessMode: Boolean = false,
  val hasSecondaryLayer: Boolean = false,
  val useAllSeriesForYRange: Boolean = false,
  val secondaryLineColor: Color? = null,
)

/**
 * Creates and remembers a [ChartConfig] for the given product.
 * Must be called from a composable (resolves theme colors).
 */
@Composable
fun rememberChartConfig(
  product: ProductSelection,
  goal: Goal? = null,
): ChartConfig {
  val primaryColor = MeTheme.colorScheme.primaryAction
  val secondaryColor = MeTheme.colorScheme.secondaryAction
  return remember(product, goal) {
    when (product) {
      is ProductSelection.MyWeight -> ChartConfig(
        lines = listOf(LineSpec(color = primaryColor)),
        goalWeight = goal?.goalWeight,
        isWeightlessMode = goal?.account?.isWeightlessOn ?: false,
        hasSecondaryLayer = true,
        useAllSeriesForYRange = false,
        secondaryLineColor = secondaryColor,
      )
      is ProductSelection.BloodPressure -> ChartConfig(
        lines = listOf(
          LineSpec(color = SnapshotColors.BloodPressure),
          LineSpec(color = Color(0xFF7B726E)),
          LineSpec(color = Color(0xFF00B3E3)),
        ),
        useAllSeriesForYRange = true,
      )
      is ProductSelection.Baby -> ChartConfig(
        lines = listOf(LineSpec(color = SnapshotColors.Baby)),
      )
    }
  }
}
