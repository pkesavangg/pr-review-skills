package com.greatergoods.meapp.features.metricinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.SegmentButtonSize
import com.greatergoods.meapp.features.common.components.SegmentButtonType
import com.greatergoods.meapp.features.common.helper.graph.dateRangeFormatter
import com.greatergoods.meapp.features.common.helper.graph.monthDayFormatter
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.metricinfo.components.MetricInfoInfoSection
import com.greatergoods.meapp.features.metricinfo.components.MetricInfoResourcesSection
import com.greatergoods.meapp.features.metricinfo.components.MetricInfoValueSection
import com.greatergoods.meapp.features.metricinfo.strings.MetricInfoStrings
import com.greatergoods.meapp.proto.MetricKey
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

data class MetricInfoKey(
  val key: MetricKey,
  val label: String,
)

@Serializable
enum class MetricInfoSource {
  DAY,
  MONTH,
}

fun getFormattedDate(timestamp: Long, source: MetricInfoSource): String {
  val formatter = when (source) {
    MetricInfoSource.DAY -> dateRangeFormatter
    MetricInfoSource.MONTH -> monthDayFormatter
  }
  val zone = ZoneId.systemDefault()
  val startDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
  return startDate.format(formatter)
}

/**
 * Main entry point for the Metric Info screen. Handles ViewModel injection and state collection.
 *
 * @param info The MetricInfoDto for the screen.
 */
@Composable
fun MetricInfoScreen(
  info: DashboardMetric,
  key: MetricKey = MetricKey.BMI,
  source: MetricInfoSource = MetricInfoSource.DAY
) {
  val viewModel = hiltViewModel<MetricInfoViewModel, MetricInfoViewModel.Factory>(
    creationCallback = { factory ->
      factory.create(info)
    },
  )
  val state by viewModel.state.collectAsState()
  if (state.stat == null) {
    return
  } else {
    MetricInfoScreenContent(
      stat = state.stat!!,
      date = getFormattedDate(
        DateTimeConverter.isoToTimestamp(info.entryTimeStamp), source,
      ),
      handleIntent = viewModel::handleIntent,
    )
  }
}

/**
 * Content composable for the Metric Info screen. Displays metric details, info, and resources.
 *
 * @param selectedSegment The currently selected metric segment.
 * @param metricValue The value of the selected metric.
 * @param metricUnit The unit of the selected metric.
 * @param metricKeys The list of available metric keys.
 * @param onSelectSegment Callback when a segment is selected.
 */
@Composable
fun MetricInfoScreenContent(
  stat: Stat,
  date: String,
  handleIntent: (MetricInfoIntent) -> Unit,
) {
  val metricKeys = MetricKey.entries
    .filter { it != MetricKey.UNRECOGNIZED }
    .map {
      MetricInfoKey(
        key = it,
        label = it.name.replace('_', ' '),
      )
    }

  val selectedMetricInfoKey = metricKeys.first { it.key == (stat.key as DashboardKey.Metric).key }

  AppScaffold(
    title = MetricInfoStrings.AppBarTitle,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    appBarColor = MeTheme.colorScheme.primaryBackground,
  ) { modifier ->
    Column(
      modifier = modifier
        .padding(horizontal = MeTheme.spacing.sm),
    ) {
      SegmentButtonGroup(
        data = metricKeys,
        selectedData = selectedMetricInfoKey,
        key = MetricInfoKey::label,
        size = SegmentButtonSize.Small,
        type = SegmentButtonType.Scrollable,
        onSelected = {
          handleIntent(MetricInfoIntent.SelectSegment(it.key))
        },
      )
      MetricInfoValueSection(value = stat.getDisplayValue(), unit = stat.unit, date = date)
      MetricInfoInfoSection()
      MetricInfoResourcesSection()
    }
  }
}

@PreviewTheme
@Composable
fun PreviewMetricInfoScreenLight() {
  MeAppTheme {
    MetricInfoScreenContent(
      stat = Stat(
        label = "Heart Rate",
        value = "18",
        unit = "bpm",
        icon = null,
        key = DashboardKey.Metric(MetricKey.HEART_RATE),
      ),
      date = "Today",
      handleIntent = {},
    )
  }
}
