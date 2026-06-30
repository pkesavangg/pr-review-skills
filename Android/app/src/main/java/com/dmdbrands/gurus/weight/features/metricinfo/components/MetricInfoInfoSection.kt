package com.dmdbrands.gurus.weight.features.metricinfo.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoDescriptions
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoStrings
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
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
      text = "${MetricInfoStrings.accWhyPrefix} ${info.title}?",
      style = MeTheme.typography.heading4,
      color = MeTheme.colorScheme.textHeading,
      // TalkBack: section header — lets users jump between sections by heading.
      modifier = Modifier.semantics { heading() },
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


