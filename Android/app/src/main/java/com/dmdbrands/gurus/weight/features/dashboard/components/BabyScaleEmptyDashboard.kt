package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.strings.ChartHeaderStrings
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Empty dashboard shown when the account owns the baby product but has no baby profile yet.
 * Renders the exact baby "no entries" first-run layout (zero value + Weight/Height toggle +
 * static grid + period tabs + connect-device CTA) — identical to a real baby's empty state,
 * just surfaced under the "Baby Scale" title. (MOB-592)
 *
 * Everything here is static (no ViewModel/data): the metric toggle switches the grid range
 * and the period tabs only toggle their own selection since there is nothing to plot.
 */
@Composable
fun BabyScaleEmptyDashboard(
  onConnectDevice: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var metric by remember { mutableStateOf(BabyMetric.WEIGHT) }
  var selectedSegment by remember { mutableStateOf(GraphSegment.WEEK) }
  val range = if (metric == BabyMetric.HEIGHT) EmptyGraphDefaults.BabyHeight else EmptyGraphDefaults.BabyWeight

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.primaryBackground)
        .padding(vertical = MeTheme.spacing.x3s),
    ) {
      Text(
        text = ChartHeaderStrings.NoEntries,
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
        modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
      )

      // Zero value + Weight/Height toggle, matching BabyChartHeader's empty state.
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.sm),
      ) {
        BabyZeroValue(metric = metric, modifier = Modifier.weight(1f))
        BabyMetricToggle(selected = metric, onSelect = { metric = it })
      }

      EmptyDashboardGraph(
        modifier = Modifier.fillMaxWidth(),
        height = 300.dp,
        range = range,
      )

      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      SegmentButtonGroup(
        data = GraphSegment.entries.toList(),
        selectedData = selectedSegment,
        key = GraphSegment::name,
        onSelected = { selectedSegment = it },
        modifier = Modifier.padding(horizontal = MeTheme.spacing.xs),
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    }

    EmptyMetric(onConnectScaleClick = onConnectDevice)
  }
}

/** Zeroed baby value — `00 lbs 0.0 oz` for weight, `0.0 in` for height (Baby purple). */
@Composable
private fun BabyZeroValue(metric: BabyMetric, modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
    when (metric) {
      BabyMetric.WEIGHT -> {
        Text(text = DashboardSnapshotStrings.ZeroBabyLbs, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = DashboardSnapshotStrings.Lbs, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = DashboardSnapshotStrings.ZeroBabyOz, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = DashboardSnapshotStrings.Oz, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
      }
      BabyMetric.HEIGHT -> {
        Text(text = DashboardSnapshotStrings.ZeroBabyOz, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = DashboardSnapshotStrings.Inches, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
      }
    }
  }
}

@PreviewTheme
@Composable
private fun BabyScaleEmptyDashboardPreview() {
  MeAppTheme {
    BabyScaleEmptyDashboard(onConnectDevice = {})
  }
}
