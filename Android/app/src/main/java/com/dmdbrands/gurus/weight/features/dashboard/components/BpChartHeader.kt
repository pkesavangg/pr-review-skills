package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.ChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.BpSystolicDiastolic
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Chart header for the Blood Pressure product — `mmHg`/`pulse` labels above the
 * sys/dia and pulse averages. The whole value block is tappable and opens
 * [BpAhaRatingsBottomSheet] for the AHA severity legend.
 */
@Composable
fun BpChartHeader(
  state: BaseDashboardState,
  segment: GraphSegment,
) {
  val segmentState = state.forSegment(segment)
  val rangeText = formatRangeText(segmentState, segment)

  val target = segmentState.target.filterIsInstance<PeriodBpmSummary>()
  val avgSys = target.map { it.avgSystolic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
  val avgDia = target.map { it.avgDiastolic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
  val avgPulse = target.map { it.avgPulse }.takeIf { it.isNotEmpty() }?.average()?.toInt()

  var showAhaSheet by remember { mutableStateOf(false) }

  ChartHeader(
    segmentState = segmentState,
    segment = segment,
    rangeData = rangeText,
    markerIndex = state.markerIndex,
  ) {
    Column(
      modifier = Modifier.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
      ) { showAhaSheet = true },
    ) {
      Row {
        Text(text = DashboardSnapshotStrings.Mmhg, style = MeTheme.typography.subHeading1, color = MeTheme.colorScheme.textSubheading)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = DashboardSnapshotStrings.Pulse, style = MeTheme.typography.subHeading1, color = MeTheme.colorScheme.textSubheading)
      }
      Row(verticalAlignment = Alignment.Bottom) {
        if (avgSys != null && avgDia != null) {
          BpSystolicDiastolic(systolic = avgSys, diastolic = avgDia, style = MeTheme.typography.heading2)
        } else {
          Text(
            text = DashboardSnapshotStrings.PlaceholderDash,
            style = MeTheme.typography.heading2,
            color = SnapshotColors.BloodPressure,
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = avgPulse?.toString() ?: DashboardSnapshotStrings.PlaceholderDash,
          style = MeTheme.typography.heading2,
          color = MeTheme.colorScheme.textSubheading,
        )
      }
    }
  }

  if (showAhaSheet) {
    BpAhaRatingsBottomSheet(onDismiss = { showAhaSheet = false })
  }
}
