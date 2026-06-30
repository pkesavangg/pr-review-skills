package com.dmdbrands.gurus.weight.features.metricinfo.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.debounceClick
import com.dmdbrands.gurus.weight.features.metricinfo.MetricInfoIntent
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoDescriptions
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

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
      // TalkBack: section header — lets users jump between sections by heading.
      modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
    resources.forEach { resource ->
      Text(
        text = resource.title.uppercase(),
        style = MeTheme.typography.link1,
        color = MeTheme.colorScheme.primaryAction,
        modifier = Modifier.debounceClick {
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

