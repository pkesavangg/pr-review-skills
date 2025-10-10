package com.dmdbrands.gurus.weight.features.metricinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoInfoSection
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoResourcesSection
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoValueSection
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoStrings
import com.dmdbrands.gurus.weight.features.metricinfo.strings.fullDateFormatter
import com.dmdbrands.gurus.weight.features.metricinfo.strings.fullMonthYearFormatter
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import kotlinx.coroutines.launch
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
    MetricInfoSource.DAY -> fullDateFormatter
    MetricInfoSource.MONTH -> fullMonthYearFormatter
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
  key: MetricKey = MetricKey.WEIGHT,
  source: MetricInfoSource = MetricInfoSource.DAY
) {
  val viewModel = hiltViewModel<MetricInfoViewModel, MetricInfoViewModel.Factory>(
    creationCallback = { factory ->
      factory.create(info, key)
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
  val scope = rememberCoroutineScope()
  val backStack = LocalNavBackStack.current
  val verticalScrollState = rememberScrollState()
  val metricKeys = MetricKey.entries.map {
      MetricInfoKey(
        key = it,
        label = it.name.replace("_", " "),
      )
    }

  val selectedMetricInfoKey = metricKeys.first { it.key == (stat.key as DashboardKey.Metric).key }

  AppScaffold(
    title = MetricInfoStrings.AppBarTitle,
    containerColor = MeTheme.colorScheme.secondaryBackground,
    appBarColor = MeTheme.colorScheme.primaryBackground,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        scope.launch {
          backStack.removeLast()
        }
      }
    },
  ) { modifier ->
    Column(
      modifier = modifier.padding(top = spacing.md).verticalScroll(verticalScrollState),
    ) {
      SegmentButtonGroup(
        data = metricKeys,
        contentPadding = PaddingValues(horizontal = MeTheme.spacing.sm),
        selectedData = selectedMetricInfoKey,
        key = MetricInfoKey::label,
        size = SegmentButtonSize.Small,
        type = SegmentButtonType.Scrollable,
        onSelected = {
          handleIntent(MetricInfoIntent.SelectSegment(it.key))
        },
      )
      Column(
        modifier = modifier
          .padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xl),
      ) {
        MetricInfoValueSection(value = stat.getDisplayValue(), unit = stat.unit, date = date)
        MetricInfoInfoSection(metricKey = selectedMetricInfoKey.key)
        MetricInfoResourcesSection(metricKey = selectedMetricInfoKey.key, handleIntent = handleIntent)
      }
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
