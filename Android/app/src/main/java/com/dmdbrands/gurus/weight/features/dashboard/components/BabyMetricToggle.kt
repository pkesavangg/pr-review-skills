package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Vertical Weight/Height pill toggle (Baby purple) shared between the real baby chart header
 * ([BabyChartHeader]) and the baby-scale empty dashboard ([BabyScaleEmptyDashboard]) so the two
 * stay in lockstep on colours, sizing and future a11y fixes. (MOB-592)
 */
@Composable
internal fun BabyMetricToggle(
  selected: BabyMetric,
  onSelect: (BabyMetric) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    BabyMetric.entries.forEach { metric ->
      val isSelected = selected == metric
      Box(
        modifier = Modifier
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
          ) { onSelect(metric) }
          .then(
            if (isSelected) Modifier.background(SnapshotColors.Baby, RoundedCornerShape(8.dp))
            else Modifier,
          )
          .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.xs),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = metric.name.uppercase(),
          style = MeTheme.typography.link1,
          color = if (isSelected) MeTheme.colorScheme.inverseAction else SnapshotColors.Baby,
        )
      }
    }
  }
}
