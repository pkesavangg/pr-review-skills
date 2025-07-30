package com.dmdbrands.gurus.weight.features.metricinfo.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoDescriptions
import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Info section explaining the metric (e.g., Why Weight?).
 */
@Composable
fun MetricInfoInfoSection(metricKey: MetricKey) {
  val info = MetricInfoDescriptions.map[metricKey]
  if (info == null) {
    return
  }
  Column {
    Text(
      text = "Why ${info.title}?",
      style = MeTheme.typography.heading4,
      color = MeTheme.colorScheme.textHeading,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    Text(
      text = info.description,
      style = MeTheme.typography.body2,
      color = MeTheme.colorScheme.textBody,
    )
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoInfoSectionLight() {
  MeAppTheme {
    MetricInfoInfoSection(MetricKey.WEIGHT)
  }
}


