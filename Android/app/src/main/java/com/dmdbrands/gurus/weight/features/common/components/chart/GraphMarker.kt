package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby.BabyDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

@Composable
internal fun rememberDefaultMarker(
  state: BaseDashboardState,
  segmentState: SegmentState,
  segment: GraphSegment,
  markerIndex: Double? = null,
  createFallbackEntry: (timestamp: Long, yValues: List<Double>, segment: GraphSegment) -> PeriodSummary? = { _, _, _ -> null },
  onTargetsUpdate: (List<PeriodSummary>) -> Unit = {},
): CartesianMarker {
  // Product-specific crosshair decoration lives here so callers don't need to know
  // which charts support a horizontal label. Currently only Baby uses it — to show
  // the CDC percentile of whatever point the user is hovering on.
  val babyState = state as? BabyDashboardState
  val horizontalLabelPosition: Position.Horizontal? = if (babyState != null) Position.Horizontal.Start else null
  val horizontalLabelFormatter: ((List<List<Double>>, Double) -> CharSequence?)? = babyState?.let {
    rememberBabyPercentileLabel(profile = it.babyProfile, metric = it.selectedMetric)
  }

  // O(1) timestamp→data lookup — replaces O(n) linear scan that ran every scrub frame.
  // Rebuilt only when segment data changes, not per-frame.
  val dataIndex: Map<Long, PeriodSummary> = remember(segmentState.data) {
    segmentState.data.associateBy { it.getTimeStamp() }
  }

  fun yLabelCallback(): (List<List<Double>>) -> Unit = { fallbackValues ->
    if (markerIndex == null || fallbackValues.isEmpty()) {
      onTargetsUpdate(emptyList())
    } else {
      val ts = markerIndex.toLong()
      // O(1) lookup for the REAL data point — has all metrics (bodyFat, bmi, etc.)
      val realEntry = dataIndex[ts]
      if (realEntry != null) {
        onTargetsUpdate(listOf(realEntry))
      } else {
        // Fallback: marker is between data points. Vico's per-layer targets
        // already exclude layers with markerTargetsEnabled=false, so flatten is safe.
        val yValues = fallbackValues.flatMap { it }
        onTargetsUpdate(listOfNotNull(createFallbackEntry(ts, yValues, segment)))
      }
    }
  }

  val openSansFamily = FontFamily(Font(R.font.open_sans_regular))

  val label =
    rememberTextComponent(
      style = TextStyle(
        fontFamily = openSansFamily,
        color = MeTheme.colorScheme.textSubheading,
        fontSize = 14.sp,
      ),
    )
  val guideline = rememberAxisLineComponent(
    fill = Fill(MeTheme.colorScheme.textBody),
    thickness = 1.dp,
  )
  val pointSize = if (segment == GraphSegment.TOTAL) 10f else 16f


  return rememberDefaultCartesianMarker(
    label = label,
    labelPosition = DefaultCartesianMarker.LabelPosition.Top,
    valueFormatter = valueFormatter(segment),
    indicator = { color ->
      ShapeComponent(
        fill = Fill(color),
        shape = CircleShape,
        strokeFill = Fill(color),
        strokeThickness = 0.dp,
      )
    },
    indicatorSize = pointSize.dp,
    guideline = guideline,
    contentPadding = Insets(vertical = 16.dp),
    yLabelCallback = yLabelCallback(),
    horizontalLabelPosition = horizontalLabelPosition,
    horizontalLabelFormatter = horizontalLabelFormatter,
  )
}

/**
 * Internal helper to remember the value formatter for the marker.
 */
@Composable
private fun valueFormatter(
  segment: GraphSegment
): DefaultCartesianMarker.ValueFormatter =
  object : DefaultCartesianMarker.ValueFormatter {
    override fun format(
      context: CartesianDrawingContext,
      targets: List<CartesianMarker.Target>,
    ) = GraphUtil.markerValueFormatter(
      targets.first().x.toLong(),
      segment,
    ).lowercase()
  }
