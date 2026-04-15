package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * BP-specific below-chart content: summary card (sys/dia + pulse average) + streaks.
 * Matches the Figma BP dashboard design.
 */
@Composable
fun BpDashboardContent(
  segmentState: SegmentState,
  state: BaseDashboardState,
) {
  val target = segmentState.target.filterIsInstance<PeriodBpmSummary>()
  val avgSys = target.map { it.avgSystolic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
  val avgDia = target.map { it.avgDiastolic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
  val avgPulse = target.map { it.avgPulse }.takeIf { it.isNotEmpty() }?.average()?.toInt()
  val entryCount = target.size

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm),
  ) {
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

    // BP Summary card
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.primaryBackground, RoundedCornerShape(9.dp))
        .padding(horizontal = MeTheme.spacing.lg, vertical = MeTheme.spacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        if (avgSys != null && avgDia != null) {
          Text(
            text = buildAnnotatedString {
              withStyle(SpanStyle(color = SnapshotColors.systolicColor(avgSys))) {
                append("$avgSys")
              }
              withStyle(SpanStyle(color = MeTheme.colorScheme.textSubheading)) {
                append("/")
              }
              withStyle(SpanStyle(color = SnapshotColors.diastolicColor(avgDia))) {
                append("$avgDia")
              }
            },
            style = MeTheme.typography.heading2,
          )
        } else {
          Text(
            text = DashboardSnapshotStrings.PlaceholderDash,
            style = MeTheme.typography.heading2,
            color = SnapshotColors.BloodPressure,
          )
        }
        Text(
          text = avgPulse?.toString() ?: DashboardSnapshotStrings.PlaceholderDash,
          style = MeTheme.typography.heading2,
          color = MeTheme.colorScheme.textSubheading,
        )
      }
      Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
      Text(
        text = if (entryCount > 0) "$entryCount ${DashboardString.Bp.EntryAverageSuffix}" else DashboardString.Bp.NoEntries,
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
    }

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

    // TODO: Add BP-specific streaks when BpDashboardState has progress field
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}
