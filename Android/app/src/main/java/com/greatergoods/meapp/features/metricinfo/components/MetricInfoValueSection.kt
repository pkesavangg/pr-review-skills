package com.greatergoods.meapp.features.metricinfo.components

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
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Section displaying the metric value, unit, and measurement date.
 *
 * @param value The metric value to display.
 * @param unit The unit of the metric value.
 */
@Composable
fun MetricInfoValueSection(value: String? = null, unit: String? = null, date: String) {
  Column(
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
    horizontalAlignment = Alignment.Start,
  ) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        text = value ?: "---",
        style = MeTheme.typography.heading2,
        color = MeTheme.colorScheme.textBody,
      )
      Spacer(modifier = Modifier.padding(horizontal = MeTheme.spacing.xs))
      if (unit != null && value != null)
        Text(
          text = unit,
          style = MeTheme.typography.heading4,
          color = MeTheme.colorScheme.textBody,
          modifier = Modifier.offset(y = (-10).dp), // shifts it slightly down like a subscript
        )
    }
    Text(
      text = MetricInfoStrings.MeasurementTaken.plus(" $date").lowercase(),
      style = MeTheme.typography.subHeading1,
      color = MeTheme.colorScheme.textSubheading,
    )
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoValueSectionLight() {
  MeAppTheme {
    MetricInfoValueSection(value = "18", unit = "bpm", date = "Today")
  }
}

