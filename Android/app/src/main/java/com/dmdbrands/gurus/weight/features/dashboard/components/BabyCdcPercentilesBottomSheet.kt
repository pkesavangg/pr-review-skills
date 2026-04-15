package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * CDC Growth Percentiles reference sheet. Opens when the user taps the Baby chart header.
 * Matches Figma node 26501:378202.
 *
 * Shows the current height + weight averages in baby-purple, each paired with the child's
 * CDC percentile for that metric. `null` values render as "--" placeholders.
 *
 * Percentile lookup against the CDC bands is a separate data-layer concern — the caller
 * supplies pre-computed values (see BabyDashboardViewModel's percentile series).
 */
@Composable
fun BabyCdcPercentilesBottomSheet(
  heightInches: Double?,
  heightPercentile: Int?,
  weightLbs: Int?,
  weightOz: Double?,
  weightPercentile: Int?,
  onDismiss: () -> Unit,
) {
  AppBottomSheet(
    title = DashboardString.Baby.CdcPercentiles.Title,
    onDismiss = onDismiss,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.secondaryBackground)
        .verticalScroll(rememberScrollState())
        .padding(vertical = MeTheme.spacing.md),
    ) {
      // Intro section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MeTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
      ) {
        Text(
          text = DashboardString.Baby.CdcPercentiles.SectionTitle,
          style = MeTheme.typography.heading4,
          color = MeTheme.colorScheme.textHeading,
        )
        Text(
          text = DashboardString.Baby.CdcPercentiles.SectionBody,
          style = MeTheme.typography.body2,
          color = MeTheme.colorScheme.textBody,
        )
      }

      Spacer(modifier = Modifier.height(MeTheme.spacing.md))

      // Height card — "21.7 inches" + "6 %"
      BabyPercentileCard(
        modifier = Modifier.padding(horizontal = MeTheme.spacing.xs),
      ) {
        BabyValueWithUnit(
          value = heightInches?.let { String.format("%.1f", it) }
            ?: DashboardString.Baby.CdcPercentiles.Placeholder,
          unit = DashboardString.Baby.CdcPercentiles.Inches,
          modifier = Modifier.weight(1f),
        )
        BabyPercentileValue(percentile = heightPercentile)
      }

      Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

      // Weight card — "14 lbs  4.4 oz" + "8 %"
      BabyPercentileCard(
        modifier = Modifier.padding(horizontal = MeTheme.spacing.xs),
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.Bottom,
        ) {
          BabyValueWithUnit(
            value = weightLbs?.toString() ?: DashboardString.Baby.CdcPercentiles.Placeholder,
            unit = DashboardString.Baby.CdcPercentiles.Lbs,
          )
          BabyValueWithUnit(
            value = weightOz?.let { String.format("%.1f", it) }
              ?: DashboardString.Baby.CdcPercentiles.Placeholder,
            unit = DashboardString.Baby.CdcPercentiles.Oz,
          )
        }
        BabyPercentileValue(percentile = weightPercentile)
      }
    }
  }
}

/**
 * White rounded 97dp card. The content lambda runs in [RowScope] so callers can
 * apply `Modifier.weight(1f)` to the left content — that lets the right-hand
 * percentile column always take its full natural width and never wrap, while the
 * left side absorbs whatever is left.
 */
@Composable
private fun BabyPercentileCard(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(97.dp)
      .background(MeTheme.colorScheme.primaryBackground, RoundedCornerShape(9.dp))
      .padding(horizontal = MeTheme.spacing.lg),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.md),
    content = content,
  )
}

/** Big purple value (heading2, ExtraBold) + small lowercase unit text trailing it at the baseline. */
@Composable
private fun BabyValueWithUnit(
  value: String,
  unit: String,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
    Text(
      text = value,
      style = MeTheme.typography.heading2,
      color = SnapshotColors.Baby,
    )
    Spacer(modifier = Modifier.padding(horizontal = 2.dp))
    Text(
      text = unit,
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
      modifier = Modifier.padding(bottom = MeTheme.spacing.xs),
    )
  }
}

/**
 * Big grey percentile number + small bottom-aligned `%` — matches Figma's
 * "21.7 inches" / "6 %" pattern (heading2 value + subHeading1 unit). The number
 * formatting (including `< 1` / `> 99` edge cases) lives in
 * [BabyPercentileHelper.formatPercentileNumber]; null renders the placeholder
 * dash alone (no `%`).
 */
@Composable
private fun BabyPercentileValue(percentile: Int?) {
  val numberLabel = BabyPercentileHelper.formatPercentileNumber(percentile)
  if (numberLabel == null) {
    Text(
      text = DashboardString.Baby.CdcPercentiles.Placeholder,
      style = MeTheme.typography.heading2,
      color = MeTheme.colorScheme.textSubheading,
    )
    return
  }
  Row(verticalAlignment = Alignment.Bottom) {
    Text(
      text = numberLabel,
      style = MeTheme.typography.heading2,
      color = MeTheme.colorScheme.textSubheading,
    )
    Text(
      text = DashboardString.Baby.CdcPercentiles.Percent,
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
      modifier = Modifier.padding(bottom = MeTheme.spacing.xs),
    )
  }
}
