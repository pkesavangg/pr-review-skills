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
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.strings.DeviceMetricsSettingStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppNote
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphLabelHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toSegment
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
      AppIconButton(
        AppIcons.Default.Close,
        modifier = Modifier.testTag(TestTags.MetricInfo.CloseButton),
        contentDescription = MetricInfoStrings.accCloseButton,
      ) {
        scope.launch {
          backStack.removeLast()
        }
      }
    },
  ) { modifier ->
    Column(
      modifier = modifier
        .padding(top = spacing.md)
        .testTag(TestTags.MetricInfo.ScreenRoot),
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

        // Per MA-3938: the label must follow graph selection state, NOT the entry DTO's
        // timestamp. Driving it off `info.entryTimeStamp` for the no-selection case is the
        // iOS bug we mirrored — it stamped the latest entry's day even though the displayed
        // value was the period average. The no-selection branch must use `info.rangeText`.
        val singleEntryDate = getFormattedDate(
          DateTimeConverter.isoToTimestamp(info.entryTimeStamp?.lastOrNull()),
          source = source,
        )

        // Per MA-3965: dashboard openings route through [GraphLabelHelper.selectionLabel]
        // — the same helper the trend-view header reads from — so the two surfaces stay
        // in lockstep. Empty-state, history-list, and missing-metric branches fall
        // outside the dashboard's selection grammar and keep their own phrasings.
        val segmentForLabel = source.toSegment()
        val measurementTakenString = when {
          info.isEmpty -> "no entries ${info.rangeText ?: singleEntryDate}"
          currentStat.getDisplayValue() == null -> MetricInfoStrings.MeasurementNotTaken
          // History list → a single concrete reading on a specific day. Phrase the
          // label that way regardless of segment; "day average" / "latest entry"
          // wording only makes sense for dashboard graph aggregates.
          info.isHistoryEntry ->
            "Measurement taken $singleEntryDate"
          info.isSingleEntry ->
            "${GraphLabelHelper.selectionLabel(
              segment = segmentForLabel,
              hasSelection = true,
              isLatestDaySelected = info.isLatestDaySelected,
            )} $singleEntryDate"
          else ->
            "${GraphLabelHelper.selectionLabel(
              segment = segmentForLabel,
              hasSelection = false,
              isLatestDaySelected = false,
            )} ${info.rangeText ?: ""}".trim()
        }

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
              message = DeviceMetricsSettingStrings.HeartRateOffNotes.Title,
              icon = AppIcons.Metrics.Pulse,
              iconType = AppIconType.Tertiary,
              buttonText = DeviceMetricsSettingStrings.HeartRateOffNotes.UpdateButton,
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
