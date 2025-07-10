package com.greatergoods.meapp.features.metricinfo.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.metricinfo.MetricInfoIntent
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoDescriptions
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoStrings
import com.greatergoods.meapp.proto.MetricKey
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Resources section for the Metric Info screen.
 */
@Composable
fun MetricInfoResourcesSection(metricKey: MetricKey, handleIntent: (MetricInfoIntent) -> Unit) {
  val resources = MetricInfoDescriptions.map[metricKey]?.resources.orEmpty()
  Column {
    Text(
      text = MetricInfoStrings.ResourcesTitle,
      style = MeTheme.typography.heading4,
      color = MeTheme.colorScheme.textBody,
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    resources.forEach { resource ->
      Text(
        text = resource.title.uppercase(),
        style = MeTheme.typography.link1,
        color = MeTheme.colorScheme.primaryAction,
        modifier = Modifier.clickable {
          handleIntent(MetricInfoIntent.OpenResource(resource.link))
        },
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    }
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoResourcesSectionLight() {
  MeAppTheme {
    MetricInfoResourcesSection(MetricKey.WEIGHT) {}
  }
}

