package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.features.common.components.chart.ChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Chart header for the Baby product — value display on the left + vertical
 * Weight/Height toggle on the right (Figma layout). The left value column is
 * tappable and opens [BabyCdcPercentilesBottomSheet] showing the baby's
 * current CDC growth percentiles.
 */
@Composable
fun BabyChartHeader(
  state: BaseDashboardState,
  segment: GraphSegment,
  handleIntent: ((BaseGraphIntent) -> Unit)? = null,
) {
  val segmentState = state.forSegment(segment)
  val rangeText = formatRangeText(segmentState, segment)
  val babyState = state as? BabyDashboardState

  var showCdcSheet by remember { mutableStateOf(false) }

  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(
      modifier = Modifier
        .weight(1f)
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
        ) { showCdcSheet = true },
    ) {
      ChartHeader(
        segmentState = segmentState,
        segment = segment,
        rangeData = rangeText,
        markerIndex = state.markerIndex,
      ) {
        BabyValueDisplay(babyState, segmentState)
      }
    }
    if (babyState != null && handleIntent != null) {
      BabyMetricToggle(babyState.selectedMetric, handleIntent)
    }
  }

  if (showCdcSheet) {
    BabyCdcSheetLauncher(
      babyState = babyState,
      segmentState = segmentState,
      onDismiss = { showCdcSheet = false },
    )
  }
}

/** Big value(s) of the active metric — `lbs / oz` for weight, `inches` for height. */
@Composable
private fun BabyValueDisplay(
  babyState: BabyDashboardState?,
  segmentState: SegmentState,
) {
  val selectedMetric = babyState?.selectedMetric ?: BabyMetric.WEIGHT
  val target = segmentState.target.filterIsInstance<PeriodBabySummary>()

  when (selectedMetric) {
    BabyMetric.WEIGHT -> {
      val avgDecigrams = target.mapNotNull { it.avgWeightDecigrams }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
      if (avgDecigrams != null) {
        val lbs = ConversionTools.convertDecigramsToLb(avgDecigrams)
        val oz = ConversionTools.convertDecigramsToOz(avgDecigrams)
        Row(verticalAlignment = Alignment.Bottom) {
          Text(text = "$lbs", style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = DashboardSnapshotStrings.Lbs, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = String.format(java.util.Locale.US, "%.1f", oz), style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = DashboardSnapshotStrings.Oz, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
        }
      } else {
        Text(text = DashboardSnapshotStrings.PlaceholderDash, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
      }
    }
    BabyMetric.HEIGHT -> {
      val avgMm = target.mapNotNull { it.avgLengthMillimeters }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
      if (avgMm != null) {
        val inches = ConversionTools.convertMmToInches(avgMm)
        Row(verticalAlignment = Alignment.Bottom) {
          Text(text = String.format(java.util.Locale.US, "%.1f", inches), style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = DashboardSnapshotStrings.Inches, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
        }
      } else {
        Text(text = DashboardSnapshotStrings.PlaceholderDash, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
      }
    }
  }
}

/** Vertical Weight/Height pill toggle on the right edge of the header. */
@Composable
private fun BabyMetricToggle(
  selected: BabyMetric,
  handleIntent: (BaseGraphIntent) -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    BabyMetric.entries.forEach { metric ->
      val isSelected = selected == metric
      Box(
        modifier = Modifier
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
          ) { handleIntent(BabyDashboardIntent.SetSelectedMetric(metric)) }
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

/**
 * Pulls the baby's average weight/length for the active segment, computes today's
 * CDC percentile via [BabyPercentileHelper], formats with ordinal suffix, and shows
 * the CDC sheet. Cached with `remember` so recompositions while the sheet is open
 * don't re-filter the 961-row M/SD list.
 */
@Composable
private fun BabyCdcSheetLauncher(
  babyState: BabyDashboardState?,
  segmentState: SegmentState,
  onDismiss: () -> Unit,
) {
  val target = segmentState.target.filterIsInstance<PeriodBabySummary>()
  val avgDecigrams = target.mapNotNull { it.avgWeightDecigrams }
    .takeIf { it.isNotEmpty() }?.average()?.toInt()
  val avgMm = target.mapNotNull { it.avgLengthMillimeters }
    .takeIf { it.isNotEmpty() }?.average()?.toInt()

  val profile = babyState?.babyProfile
  val sex = profile?.sex
  val birthDateMillis = profile?.birthdate?.let { DateTimeConverter.isoToTimestamp(it) }
  val now = remember { System.currentTimeMillis() }

  val weightPct = remember(avgDecigrams, birthDateMillis, sex, now) {
    if (avgDecigrams != null && birthDateMillis != null) {
      BabyPercentileHelper.calcPercentile(
        sex = sex,
        birthDateMillis = birthDateMillis,
        value = avgDecigrams.toDouble(),
        type = BabyPercentileHelper.MeasurementType.WEIGHT,
        entryTimestampMillis = now,
      )
    } else {
      null
    }
  }
  val lengthPct = remember(avgMm, birthDateMillis, sex, now) {
    if (avgMm != null && birthDateMillis != null) {
      BabyPercentileHelper.calcPercentile(
        sex = sex,
        birthDateMillis = birthDateMillis,
        value = avgMm.toDouble(),
        type = BabyPercentileHelper.MeasurementType.LENGTH,
        entryTimestampMillis = now,
      )
    } else {
      null
    }
  }

  BabyCdcPercentilesBottomSheet(
    heightInches = avgMm?.let { ConversionTools.convertMmToInches(it) },
    heightPercentile = lengthPct,
    weightLbs = avgDecigrams?.let { ConversionTools.convertDecigramsToLb(it) },
    weightOz = avgDecigrams?.let { ConversionTools.convertDecigramsToOz(it) },
    weightPercentile = weightPct,
    onDismiss = onDismiss,
  )
}
