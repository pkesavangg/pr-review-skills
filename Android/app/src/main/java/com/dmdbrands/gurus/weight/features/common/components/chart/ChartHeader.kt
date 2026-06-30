package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Shared chart header: product-specific value slot + range text.
 * Row 1 ("week average" label) is handled by ChartHeaderLabel in GraphPagerView.
 */
@Composable
fun ChartHeader(
  rangeData: String,
  markerIndex: Double? = null,
  valueContent: @Composable () -> Unit,
) {
  Column(
    // TalkBack: read the value + range as one chart-summary announcement.
    modifier = Modifier
      .padding(horizontal = MeTheme.spacing.sm)
      .semantics(mergeDescendants = true) {},
  ) {
    valueContent()
    Text(
      text = rangeData.lowercase(),
      style = MeTheme.typography.subHeading2,
      color = if (markerIndex == null) MeTheme.colorScheme.textSubheading else Color.Transparent,
    )
  }
}
