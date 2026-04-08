package com.dmdbrands.gurus.weight.features.common.components.chart.bp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Chart header for BP: shows systolic/diastolic + pulse values
 * with severity-based colors, matching the Figma snapshot card style.
 */
@Composable
fun BpChartHeader(
  segmentState: SegmentState = SegmentState(),
  segment: GraphSegment,
  systolic: Int?,
  diastolic: Int?,
  pulse: Int?,
  rangeData: String,
) {
  val headerText = if (segmentState.markerIndex != null) {
    when (segment) {
      GraphSegment.WEEK, GraphSegment.MONTH -> "day"
      else -> "month"
    }
  } else segment.name.lowercase()

  Column(
    modifier = Modifier.padding(
      horizontal = MeTheme.spacing.sm,
      vertical = MeTheme.spacing.x3s,
    ),
  ) {
    Row {
      Text(
        text = if (segmentState.isEmptyGraph) "no entries" else "mmhg",
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
      Spacer(modifier = Modifier.weight(1f))
      Text(
        text = "pulse",
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
    }

    Row(verticalAlignment = Alignment.Bottom) {
      if (systolic != null && diastolic != null) {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = SnapshotColors.systolicColor(systolic))) {
              append("$systolic")
            }
            withStyle(SpanStyle(color = MeTheme.colorScheme.textSubheading)) {
              append("/")
            }
            withStyle(SpanStyle(color = SnapshotColors.diastolicColor(diastolic))) {
              append("$diastolic")
            }
          },
          style = MeTheme.typography.heading2,
        )
      } else {
        Text(
          text = "—",
          style = MeTheme.typography.heading2,
          color = SnapshotColors.BloodPressure,
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      Text(
        text = pulse?.toString() ?: "—",
        style = MeTheme.typography.heading2,
        color = if (pulse != null) SnapshotColors.pulseColor(pulse) else MeTheme.colorScheme.textSubheading,
      )
    }

    Text(
      text = rangeData.lowercase(),
      style = MeTheme.typography.subHeading2,
      color = if (segmentState.markerIndex == null) MeTheme.colorScheme.textSubheading else Color.Transparent,
    )
  }
}
