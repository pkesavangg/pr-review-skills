package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.BpSystolicDiastolic
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp.BpDashboardState
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString as MilestoneStrings

/**
 * BP-specific below-chart content: summary card (sys/dia + pulse average) + streak cards.
 * Matches the Figma BP dashboard design.
 */
@Composable
fun BpDashboardContent(
  state: BpDashboardState,
) {
  // "Three entry average" is account-scoped, derived from the last N per-day BP averages
  // in state — independent of the chart's selected window.
  val lastReadings = state.lastReadings
  val avgSys = lastReadings.averageSystolic
  val avgDia = lastReadings.averageDiastolic
  val avgPulse = lastReadings.averagePulse
  val entryCount = lastReadings.entries.size

  var showThreeReadingSheet by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm),
  ) {
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

    // BP Summary card — tap to open Three Reading Average sheet
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.primaryBackground, RoundedCornerShape(9.dp))
        .clickable { showThreeReadingSheet = true }
        .padding(horizontal = MeTheme.spacing.lg, vertical = MeTheme.spacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        if (avgSys != null && avgDia != null) {
          BpSystolicDiastolic(
            systolic = avgSys,
            diastolic = avgDia,
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
        text = DashboardString.Bp.entryAverageLabel(entryCount),
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
    }

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

    // Streak cards row — current streak (bolt) + longest streak (flame)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
      BpStreakCard(
        icon = AppIcons.Milestone.Bolt,
        value = state.progress.streak.current,
        label = MilestoneStrings.MileStone.CurrentStreak,
        modifier = Modifier.weight(1f),
      )
      BpStreakCard(
        icon = AppIcons.Milestone.Streak,
        value = state.progress.streak.longest,
        label = MilestoneStrings.MileStone.LongestStreak,
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }

  if (showThreeReadingSheet) {
    val readings = lastReadings.entries.map {
      BpReadingRow(systolic = it.avgSystolic, diastolic = it.avgDiastolic, pulse = it.avgPulse)
    }
    BpThreeReadingAverageBottomSheet(
      averageSystolic = avgSys,
      averageDiastolic = avgDia,
      averagePulse = avgPulse,
      entryCount = entryCount,
      readings = readings,
      onDismiss = { showThreeReadingSheet = false },
    )
  }
}

@Composable
private fun BpStreakCard(
  icon: Int,
  value: Int,
  label: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .background(MeTheme.colorScheme.primaryBackground, RoundedCornerShape(MeTheme.borderRadius.sm))
      .padding(MeTheme.spacing.sm),
    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.x2s),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppIcon(
      id = icon,
      contentDescription = label,
      tintColor = MeTheme.colorScheme.streak,
      modifier = Modifier.size(40.dp),
    )
    Column {
      Text(
        text = "$value ${if (value == 1) "day" else "days"}",
        style = MeTheme.typography.heading4,
        color = MeTheme.colorScheme.textHeading,
      )
      Text(
        text = label,
        style = MeTheme.typography.subHeading2,
        color = MeTheme.colorScheme.textSubheading,
      )
    }
  }
}
