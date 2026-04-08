package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Shared chart header: "week/day/month average" + product-specific value slot + range text.
 * Row 1 and Row 3 are shared. Row 2 is product-specific via [valueContent] slot.
 */
@Composable
fun ChartHeader(
  segmentState: SegmentState,
  segment: GraphSegment,
  rangeData: String,
  markerIndex: Double? = null,
  valueContent: @Composable () -> Unit,
) {
  val headerText = if (markerIndex != null) {
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
    // Row 1: shared — "week average" / "day average" / "no entries"
    Text(
      text = if (segmentState.isEmptyGraph) "no entries" else "$headerText average",
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
    )

    // Row 2: product-specific value display
    valueContent()

    // Row 3: shared — range text
    Text(
      text = rangeData.lowercase(),
      style = MeTheme.typography.subHeading2,
      color = if (markerIndex == null) MeTheme.colorScheme.textSubheading else Color.Transparent,
    )
  }
}
