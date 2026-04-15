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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.chart.ChartHeader
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.BpSystolicDiastolic
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotColors
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyMetric
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.theme.MeTheme

private fun getDisplayUnit(weightUnit: WeightUnit, weight: Double): String = when (weightUnit) {
  WeightUnit.KG -> DashboardSnapshotStrings.Kg
  WeightUnit.LB -> if (weight <= 1.0 && weight != 0.0) DashboardSnapshotStrings.Lb else DashboardSnapshotStrings.Lbs
}

@Composable
fun DashboardChartHeader(
  state: BaseDashboardState,
  segment: GraphSegment,
  product: ProductSelection,
  handleIntent: ((BaseGraphIntent) -> Unit)? = null,
) {
  val segmentState = state.forSegment(segment)
  val rangeText = (segmentState.visibleMin ?: segmentState.chartMinX?.toLong())?.let { min ->
    (segmentState.visibleMax ?: segmentState.chartMaxX?.toLong())?.let { max -> GraphUtil.formatDateRange(min, max, segment) }
  } ?: ""

  // Baby: wrap header + vertical toggle in a Row
  if (product is ProductSelection.Baby) {
    val babyState = state as? BabyDashboardState
    var showCdcSheet by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
      // Left: header content (week average, value, date range) — tap to open CDC sheet
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
      // Right: vertical Weight/Height toggle (Figma: column at header level)
      if (babyState != null && handleIntent != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          BabyMetric.entries.forEach { metric ->
            val isSelected = babyState.selectedMetric == metric
            Box(
              modifier = Modifier
                .clickable(
                  indication = null,
                  interactionSource = remember { MutableInteractionSource() },
                ) { handleIntent(BabyDashboardIntent.SetSelectedMetric(metric)) }
                .then(
                  if (isSelected) Modifier.background(SnapshotColors.Baby, RoundedCornerShape(8.dp))
                  else Modifier
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
    }

    if (showCdcSheet) {
      // Pull current baby weight + length averages from the active segment so the sheet
      // reflects whatever window the chart is showing. Percentile values are left null for
      // now — data-layer hook into BabyPercentileHelper is a follow-up.
      val target = segmentState.target.filterIsInstance<PeriodBabySummary>()
      val avgDecigrams = target.mapNotNull { it.avgWeightDecigrams }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
      val avgMm = target.mapNotNull { it.avgLengthMillimeters }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
      BabyCdcPercentilesBottomSheet(
        heightInches = avgMm?.let { ConversionTools.convertMmToInches(it) },
        heightPercentile = null,
        weightLbs = avgDecigrams?.let { ConversionTools.convertDecigramsToLb(it) },
        weightOz = avgDecigrams?.let { ConversionTools.convertDecigramsToOz(it) },
        weightPercentile = null,
        onDismiss = { showCdcSheet = false },
      )
    }
  } else {
    ChartHeader(
      segmentState = segmentState,
      segment = segment,
      rangeData = rangeText,
      markerIndex = state.markerIndex,
    ) {
      when (product) {
        is ProductSelection.MyWeight -> {
          val weightState = state as? WeightDashboardState
          val target = segmentState.target.filterIsInstance<PeriodBodyScaleSummary>()
          val avgLb = if (target.isEmpty()) 0.0 else target.map { it.weight }.average()
          // Default must match ProductChart.kt default (WeightUnit.LB) so header
          // and axis labels stay consistent when weightState is null.
          val weightUnit = weightState?.weightUnit ?: WeightUnit.LB
          val weightless = weightState?.weightless
          val weightlessOffset = if (weightless?.isWeightlessOn == true) weightless.weightlessWeight.toDouble() else 0.0
          val displayValue = convertWeight(avgLb - weightlessOffset, WeightUnit.LB, weightUnit)
          val label = if (target.isEmpty()) "000.0" else formatWeightValue(displayValue)
          val displayUnit = remember(weightUnit, displayValue) { getDisplayUnit(weightUnit, displayValue) }

          Row(verticalAlignment = Alignment.Bottom) {
            Text(text = label, style = MeTheme.typography.heading2, color = MeTheme.colorScheme.textBody)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = displayUnit, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
          }
        }

        is ProductSelection.BloodPressure -> {
          val target = segmentState.target.filterIsInstance<PeriodBpmSummary>()
          val avgSys = target.map { it.avgSystolic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
          val avgDia = target.map { it.avgDiastolic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
          val avgPulse = target.map { it.avgPulse }.takeIf { it.isNotEmpty() }?.average()?.toInt()

          var showAhaSheet by remember { mutableStateOf(false) }

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
                BpSystolicDiastolic(
                  systolic = avgSys,
                  diastolic = avgDia,
                  style = MeTheme.typography.heading2,
                )
              } else {
                Text(text = DashboardSnapshotStrings.PlaceholderDash, style = MeTheme.typography.heading2, color = SnapshotColors.BloodPressure)
              }
              Spacer(modifier = Modifier.weight(1f))
              Text(
                text = avgPulse?.toString() ?: DashboardSnapshotStrings.PlaceholderDash,
                style = MeTheme.typography.heading2,
                color = MeTheme.colorScheme.textSubheading,
              )
            }
          }

          if (showAhaSheet) {
            BpAhaRatingsBottomSheet(onDismiss = { showAhaSheet = false })
          }
        }

        else -> {} // Baby handled above
      }
    }
  }
}

@Composable
private fun BabyValueDisplay(
  babyState: BabyDashboardState?,
  segmentState: com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState,
) {
  val selectedMetric = babyState?.selectedMetric ?: BabyMetric.WEIGHT
  val target = segmentState.target.filterIsInstance<PeriodBabySummary>()

  when (selectedMetric) {
    BabyMetric.WEIGHT -> {
      val avgDecigrams = target.mapNotNull { it.avgWeightDecigrams }.takeIf { it.isNotEmpty() }
        ?.average()?.toInt()
      if (avgDecigrams != null) {
        val lbs = ConversionTools.convertDecigramsToLb(avgDecigrams)
        val oz = ConversionTools.convertDecigramsToOz(avgDecigrams)
        Row(verticalAlignment = Alignment.Bottom) {
          Text(text = "$lbs", style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = DashboardSnapshotStrings.Lbs, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = String.format("%.1f", oz), style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = DashboardSnapshotStrings.Oz, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
        }
      } else {
        Text(text = DashboardSnapshotStrings.PlaceholderDash, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
      }
    }
    BabyMetric.HEIGHT -> {
      val avgMm = target.mapNotNull { it.avgLengthMillimeters }.takeIf { it.isNotEmpty() }
        ?.average()?.toInt()
      if (avgMm != null) {
        val inches = ConversionTools.convertMmToInches(avgMm)
        Row(verticalAlignment = Alignment.Bottom) {
          Text(text = String.format("%.1f", inches), style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = DashboardSnapshotStrings.Inches, style = MeTheme.typography.subHeading2, color = MeTheme.colorScheme.textSubheading, modifier = Modifier.offset(y = (-10).dp))
        }
      } else {
        Text(text = DashboardSnapshotStrings.PlaceholderDash, style = MeTheme.typography.heading2, color = SnapshotColors.Baby)
      }
    }
  }
}
