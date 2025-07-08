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
 * Resources section for the Metric Info screen.
 */
@Composable
fun MetricInfoResourcesSection() {
  Column {
    Text(
      text = MetricInfoStrings.ResourcesTitle,
      style = MeTheme.typography.heading4,
      color = MeTheme.colorScheme.textBody,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    Text(
      text = MetricInfoStrings.ResourceCdc,
      style = MeTheme.typography.link1,
      color = MeTheme.colorScheme.primaryAction,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    Text(
      text = MetricInfoStrings.ResourceClevelandClinic,
      style = MeTheme.typography.link1,
      color = MeTheme.colorScheme.primaryAction,
    )
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoResourcesSectionLight() {
  MeAppTheme {
    MetricInfoResourcesSection()
  }
}

