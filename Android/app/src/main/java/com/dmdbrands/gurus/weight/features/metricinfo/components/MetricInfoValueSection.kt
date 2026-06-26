package com.dmdbrands.gurus.weight.features.metricinfo.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Section displaying the metric value, unit, and measurement date.
 *
 * @param value The metric value to display.
 * @param unit The unit of the metric value.
 */
@Composable
fun MetricInfoValueSection(subText : String , value: String? = null, unit: String? = null, valuePrefix: String? = null) {
  Column(
    // TalkBack: read the value, unit and measurement date as a single announcement
    // rather than three separate swipe stops.
    modifier = Modifier.semantics(mergeDescendants = true) {},
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
    horizontalAlignment = Alignment.Start,
  ) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        text = value ?: if(valuePrefix?.isNotEmpty() == true) "$valuePrefix --" else "--",
        style = MeTheme.typography.heading2,
        color = MeTheme.colorScheme.textBody,
      )
      Spacer(modifier = Modifier.padding(horizontal = MeTheme.spacing.x2s))
      if (unit != null)
        Text(
          text = unit,
          style = MeTheme.typography.heading4,
          color = MeTheme.colorScheme.textBody,
          modifier = Modifier.offset(y = (-10).dp), // shifts it slightly down like a subscript
        )
    }
    Text(
      text = subText,
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
    )
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoValueSectionLight() {
  MeAppTheme {
    MetricInfoValueSection(value = "18", unit = "bpm", subText = "Today")
  }
}

