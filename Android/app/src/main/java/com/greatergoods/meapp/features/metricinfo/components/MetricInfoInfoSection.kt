package com.greatergoods.meapp.features.metricinfo.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Info section explaining the metric (e.g., Why Weight?).
 */
@Composable
fun MetricInfoInfoSection() {
  Column {
    Text(
      text = MetricInfoStrings.WhyWeightTitle,
      style = MeTheme.typography.heading4,
      color = MeTheme.colorScheme.textBody,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    Text(
      text = MetricInfoStrings.WhyWeightBody,
      style = MeTheme.typography.body2,
      color = MeTheme.colorScheme.textBody,
    )
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoInfoSectionLight() {
  MeAppTheme {
    MetricInfoInfoSection()
  }
}


