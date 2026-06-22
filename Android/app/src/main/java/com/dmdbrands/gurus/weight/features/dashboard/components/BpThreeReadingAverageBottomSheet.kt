package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppBottomSheet
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.BpSystolicDiastolic
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Display model for a single BP reading row shown in the Three Reading Average sheet.
 * Kept local to the UI layer; caller maps real data to this shape.
 */
data class BpReadingRow(
  val systolic: Int,
  val diastolic: Int,
  val pulse: Int,
)

/**
 * Bottom sheet shown when the user taps the "three entry average" card on the BP dashboard.
 * Matches Figma node 26501:378230 (Three Reading Average).
 *
 * Built on top of [AppBottomSheet] which provides the shared header/drag-handle pattern.
 */
@Composable
fun BpThreeReadingAverageBottomSheet(
  averageSystolic: Int?,
  averageDiastolic: Int?,
  averagePulse: Int?,
  entryCount: Int,
  readings: List<BpReadingRow>,
  onDismiss: () -> Unit,
) {
  AppBottomSheet(
    title = DashboardString.Bp.ThreeReadingAverage.Title,
    onDismiss = onDismiss,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.secondaryBackground)
        .verticalScroll(rememberScrollState())
        .padding(vertical = MeTheme.spacing.md),
    ) {
      // Why section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MeTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
      ) {
        Text(
          text = DashboardString.Bp.ThreeReadingAverage.WhyTitle,
          style = MeTheme.typography.heading4,
          color = MeTheme.colorScheme.textHeading,
        )
        Text(
          text = DashboardString.Bp.ThreeReadingAverage.WhyBody,
          style = MeTheme.typography.body2,
          color = MeTheme.colorScheme.textBody,
        )
      }

      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

      // Three-entry average card
      BpAverageCard(
        systolic = averageSystolic,
        diastolic = averageDiastolic,
        pulse = averagePulse,
        entryCount = entryCount,
        modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
      )

      Spacer(modifier = Modifier.height(MeTheme.spacing.md))

      // "Last 3 readings" section title
      Text(
        text = DashboardString.Bp.ThreeReadingAverage.LastReadingsTitle,
        style = MeTheme.typography.heading4,
        color = MeTheme.colorScheme.textHeading,
        modifier = Modifier.padding(horizontal = MeTheme.spacing.md),
      )

      Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

      // Up to 3 reading cards, 16dp (sm) gap between them
      readings.take(3).forEachIndexed { index, reading ->
        if (index > 0) Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
        BpReadingCard(
          reading = reading,
          modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        )
      }
    }
  }
}

@Composable
private fun BpAverageCard(
  systolic: Int?,
  diastolic: Int?,
  pulse: Int?,
  entryCount: Int,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .height(119.dp)
      .background(MeTheme.colorScheme.primaryBackground, RoundedCornerShape(9.dp))
      .padding(horizontal = MeTheme.spacing.lg),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(48.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      if (systolic != null && diastolic != null) {
        BpSystolicDiastolic(
          systolic = systolic,
          diastolic = diastolic,
          style = MeTheme.typography.heading2,
        )
      } else {
        Text(text = "--", style = MeTheme.typography.heading2, color = SnapshotColors.BloodPressure)
      }
      Text(
        text = pulse?.toString() ?: "--",
        style = MeTheme.typography.heading2,
        color = MeTheme.colorScheme.textSubheading,
      )
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
    Text(
      text = DashboardString.Bp.entryAverageLabel(entryCount),
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
    )
  }
}

@Composable
private fun BpReadingCard(
  reading: BpReadingRow,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(119.dp)
      .background(MeTheme.colorScheme.primaryBackground, RoundedCornerShape(MeTheme.borderRadius.sm)),
    horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BpSystolicDiastolic(
        systolic = reading.systolic,
        diastolic = reading.diastolic,
        style = MeTheme.typography.heading2,
      )
      Text(
        text = DashboardString.Bp.ThreeReadingAverage.Mmhg,
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = reading.pulse.toString(),
        style = MeTheme.typography.heading2,
        color = MeTheme.colorScheme.textSubheading,
      )
      Text(
        text = DashboardString.Bp.ThreeReadingAverage.Pulse,
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
    }
  }
}
