package com.greatergoods.meapp.features.metricinfo.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoDescriptions
import com.greatergoods.meapp.proto.MetricKey
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

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


