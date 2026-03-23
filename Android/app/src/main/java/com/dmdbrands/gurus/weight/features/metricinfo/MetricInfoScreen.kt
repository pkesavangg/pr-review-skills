package com.dmdbrands.gurus.weight.features.metricinfo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.strings.ScaleMetricsSettingStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoInfoSection
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoResourcesSection
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoValueSection
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoStrings
import com.dmdbrands.gurus.weight.features.metricinfo.strings.fullDateFormatter
import com.dmdbrands.gurus.weight.features.metricinfo.strings.fullMonthYearFormatter
import com.dmdbrands.gurus.weight.resources.AppIcons
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
  WEEK,
  MONTH,
  YEAR,
  TOTAL
}

/**
 * Gets filtered metric keys based on dashboard type.
 * For 4-metric dashboard, returns only BMI, BODY_FAT, MUSCLE_MASS, BODY_WATER.
 * For 12-metric dashboard, returns all available metrics.
 * WEIGHT is always included regardless of dashboard type.
 *
 * @param dashboardType The dashboard type to determine which metrics to show.
 * @return List of filtered MetricKey values with WEIGHT always included.
 */
fun getFilteredMetricKeys(dashboardType: DashboardType = DashboardType.DASHBOARD_12_METRICS): List<MetricKey> {
  val filteredMetrics = when (dashboardType) {
    DashboardType.DASHBOARD_4_METRICS -> MetricKey.getDefault4Metrics()
    DashboardType.DASHBOARD_12_METRICS -> MetricKey.getAllMetrics()
  }

  // Always include WEIGHT, placing it first if not already present
  return if (filteredMetrics.contains(MetricKey.WEIGHT)) {
    filteredMetrics
  } else {
    listOf(MetricKey.WEIGHT) + filteredMetrics
  }
}

fun getFormattedDate(timestamp: Long, source: MetricInfoSource): String {
  val formatter = when (source) {
    MetricInfoSource.WEEK, MetricInfoSource.MONTH -> fullDateFormatter
    MetricInfoSource.YEAR, MetricInfoSource.TOTAL -> fullMonthYearFormatter
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
  source: MetricInfoSource = MetricInfoSource.WEEK
) {
  val viewModel = hiltViewModel<MetricInfoViewModel, MetricInfoViewModel.Factory>(
    creationCallback = { factory ->
      factory.create(info, key)
    },
  )
  val state by viewModel.state.collectAsStateWithLifecycle()
  val stat = state.stat ?: return
  MetricInfoScreenContent(
    stat = stat,
    info = info,
    source = source,
    selectedIndex = state.selectedMetricIndex,
    handleIntent = viewModel::handleIntent,
    metricInfoState = state,
  )
}

/**
 * Content composable for the Metric Info screen. Displays metric details, info, and resources.
 *
 * @param stat The currently selected metric stat.
 * @param info The dashboard metric data.
 * @param selectedIndex The currently selected metric index.
 * @param date The formatted date string.
 * @param handleIntent Callback for handling intents.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricInfoScreenContent(
  metricInfoState: MetricInfoState,
  stat: Stat,
  info: DashboardMetric,
  selectedIndex: Int,
  source: MetricInfoSource,
  handleIntent: (MetricInfoIntent) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val backStack = LocalNavBackStack.current

  val dashboardType = metricInfoState.dashboardType ?: DashboardType.DASHBOARD_12_METRICS
  val filteredMetricKeys = getFilteredMetricKeys(dashboardType)


  val metricKeys = filteredMetricKeys.map {
    MetricInfoKey(
      key = it,
      label = it.name.replace("_", " "),
    )
  }

  val pagerState = rememberPagerState(
    initialPage = selectedIndex,
    pageCount = { metricKeys.size },
  )

  val selectedMetricInfoKey = metricKeys.firstOrNull {
    it.key == (stat.key as DashboardKey.Metric).key
  } ?: metricKeys.firstOrNull()

  // Sync pager state with selected index
  LaunchedEffect(selectedIndex) {
    if (selectedIndex != pagerState.currentPage && selectedIndex in metricKeys.indices) {
      pagerState.scrollToPage(selectedIndex)
    }
  }

  // Handle pager page changes
  LaunchedEffect(pagerState.currentPage) {
    if (pagerState.currentPage != selectedIndex) {
      handleIntent(MetricInfoIntent.SetSelectedIndex(pagerState.currentPage))
    }
  }

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
      modifier = modifier.padding(top = spacing.md),
    ) {
      if (metricKeys.isNotEmpty() && selectedMetricInfoKey != null) {
        SegmentButtonGroup(
          data = metricKeys,
          contentPadding = PaddingValues(horizontal = spacing.sm),
          selectedData = metricKeys.get(selectedIndex),
          key = MetricInfoKey::label,
          size = SegmentButtonSize.Small,
          type = SegmentButtonType.Scrollable,
          onSelected = {
            handleIntent(MetricInfoIntent.SelectSegment(it.key))
          },
        )
      }

      HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
      ) { page ->
        val currentMetricKey = metricKeys[page].key
        val currentStat = StatHelper.getMetricValue(info, currentMetricKey)
        val pageScrollState = rememberScrollState()

        val date = when {
          info.isSingleEntry -> getFormattedDate(
            DateTimeConverter.isoToTimestamp(info.entryTimeStamp?.lastOrNull()), source = source,
          )

          info.rangeText != null -> info.rangeText
          else -> ""
        }

        val sourceName = when {
          info.isSingleEntry -> if (source == MetricInfoSource.WEEK || source == MetricInfoSource.MONTH) "day" else "month"
          else -> source.name.lowercase()
        }

        val measurementTakenString = if (info.isEmpty)
          "no entries".plus(" $date")
        else if (currentStat.getDisplayValue() != null)
          sourceName.plus(" average $date").lowercase()
        else
          MetricInfoStrings.MeasurementNotTaken

        Column(
          modifier = modifier
            .fillMaxSize()
            .verticalScroll(pageScrollState)
            .padding(horizontal = spacing.sm, vertical = spacing.md),
          verticalArrangement = Arrangement.Top,
        ) {
          MetricInfoValueSection(
            value = currentStat.getDisplayValue(),
            valuePrefix = currentStat.valuePrefix,
            unit = currentStat.unit,
            subText = measurementTakenString,
          )

          Spacer(modifier = Modifier.height(spacing.xl))

          if (metricInfoState.isHeartRateOff && currentMetricKey == MetricKey.HEART_RATE) {
            AppNote(
              message = ScaleMetricsSettingStrings.HeartRateOffNotes.Title,
              icon = AppIcons.Metrics.Pulse,
              iconType = AppIconType.Tertiary,
              buttonText = ScaleMetricsSettingStrings.HeartRateOffNotes.UpdateButton,
              onButtonClick = { handleIntent(MetricInfoIntent.UpdateScaleMode) },
            )
            Spacer(modifier = Modifier.height(spacing.xl))
          }

          MetricInfoInfoSection(metricKey = currentMetricKey)

          Spacer(modifier = Modifier.height(spacing.xl))

          MetricInfoResourcesSection(metricKey = currentMetricKey, handleIntent = handleIntent)
        }
      }
    }
  }
}
