package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.reorderable.ReorderableItem
import com.dmdbrands.gurus.weight.features.common.components.reorderable.rememberReorderableLazyGridState
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.StatHelper
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Composable for the dashboard metrics section that displays health metrics in a grid layout.
 *
 * @param metricData List of period body scale summaries for calculating metrics
 * @param inEditMode Whether the dashboard is in edit mode
 * @param visibleKeys List of currently visible dashboard keys
 * @param selectedStat Currently selected stat for highlighting
 * @param dashboardType The dashboard type (4 or 12 metrics)
 * @param onMetricClick Callback when a metric is clicked
 * @param onMetricsChanged Callback when visible metrics are changed (for save functionality)
 */
@Composable
fun DashboardMetrics(
  metricData: List<PeriodBodyScaleSummary>,
  inEditMode: Boolean = false,
  visibleKeys: List<DashboardKey> = listOf(),
  selectedStat: Stat? = null,
  isFromSetup: Boolean = false,
  dashboardType: DashboardType = DashboardType.DASHBOARD_12_METRICS,
  onMetricClick: (Stat?) -> Unit = {},
  onMetricsChanged: (List<DashboardKey>) -> Unit = { }
) {

  val dashboardMetric = remember(metricData) {
    if (metricData.isNotEmpty()) DashboardMetric.fromPeriodSummaries(metricData) else DashboardMetric.empty()
  }

  val metricKeys = remember(visibleKeys) {
    visibleKeys.mapNotNull { key ->
      when (key) {
        is DashboardKey.Metric -> key.key
        is DashboardKey.Milestone -> null
      }
    }
  }
  // Cache metrics calculations with proper keys to avoid recomputation
  val visibleMetrics = remember(dashboardMetric, metricKeys, isFromSetup, dashboardType) {
    val allMetrics = StatHelper.getMetrics(
      dashboardMetric,
      visibleKeys = metricKeys,
      useShort = true,
      showMetricIcon = isFromSetup,
      filterNulls = false,
    )
    // Filter metrics based on dashboard type
    when (dashboardType) {
      DashboardType.DASHBOARD_4_METRICS -> {
        val allowedKeys = listOf(
          DashboardKey.Metric(MetricKey.BMI),
          DashboardKey.Metric(MetricKey.BODY_FAT),
          DashboardKey.Metric(MetricKey.MUSCLE_MASS),
          DashboardKey.Metric(MetricKey.BODY_WATER),
        )
        allMetrics.filter { stat ->
          stat.key in allowedKeys
        }
      }

      DashboardType.DASHBOARD_12_METRICS -> allMetrics
    }
  }

  val allMetrics = remember(dashboardMetric, isFromSetup, dashboardType) {
    val allAvailableMetrics = StatHelper.getMetrics(
      dashboardMetric,
      visibleKeys = null,
      useShort = true,
      showMetricIcon = isFromSetup,
      filterNulls = false,
    )
    // Filter all metrics based on dashboard type
    when (dashboardType) {
      DashboardType.DASHBOARD_4_METRICS -> {
        val allowedKeys = listOf(
          DashboardKey.Metric(MetricKey.BMI),
          DashboardKey.Metric(MetricKey.BODY_FAT),
          DashboardKey.Metric(MetricKey.MUSCLE_MASS),
          DashboardKey.Metric(MetricKey.BODY_WATER),
        )
        allAvailableMetrics.filter { stat ->
          stat.key in allowedKeys
        }
      }

      DashboardType.DASHBOARD_12_METRICS -> allAvailableMetrics
    }
  }

  val hiddenMetrics = remember(visibleMetrics, allMetrics) {
    allMetrics.filter { it !in visibleMetrics }
  }

  val onMetricMoved = { fromVisible: Boolean, toVisible: Boolean, metric: Stat ->
    val metricKey = metric.key
    var localVisibleKeys: List<DashboardKey> = emptyList()
    localVisibleKeys = if (fromVisible && !toVisible) {
      visibleKeys.filterNot { it == metricKey }
    } else if (!fromVisible && toVisible) {
      visibleKeys + metricKey
    } else {
      emptyList()
    }
    onMetricsChanged(localVisibleKeys)

  }

  DashboardMetricsGrid(
    visibleMetrics = visibleMetrics,
    hiddenMetrics = hiddenMetrics,
    inEditMode = inEditMode,
    selectedStat = selectedStat,
    onMetricClick = onMetricClick,
    onMetricMoved = onMetricMoved,
    isFromSetup = isFromSetup,
    dashboardType = dashboardType,
    onReorder = {
      val localVisibleKeys = it.map { it.key }
      onMetricsChanged(localVisibleKeys)
    },
  )
}

/**
 * Grid layout for displaying dashboard metrics.
 */
@Composable
private fun DashboardMetricsGrid(
  visibleMetrics: List<Stat>,
  hiddenMetrics: List<Stat>,
  inEditMode: Boolean,
  isFromSetup: Boolean,
  selectedStat: Stat?,
  onMetricClick: (Stat?) -> Unit,
  onReorder: (List<Stat>) -> Unit,
  dashboardType: DashboardType,
  onMetricMoved: (fromVisible: Boolean, toVisible: Boolean, metric: Stat) -> Unit
) {
  val hapticFeedback = LocalHapticFeedback.current
  val lazyGridState = rememberLazyGridState()
  val currentDeviceType = getDeviceType()
  val reorderableState = rememberReorderableLazyGridState(
    lazyGridState = lazyGridState,
    onMove = { from, to ->
      val localVisibleMetrics = visibleMetrics.toMutableList().apply {
        add(to.index, removeAt(from.index))
      }
      onReorder(localVisibleMetrics)
      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    },
  )

  val columnCount = when (currentDeviceType) {
    DeviceType.Tablet -> 4
    else -> if (dashboardType == DashboardType.DASHBOARD_12_METRICS) 3 else 2
  }
  LazyVerticalGrid(
    columns = GridCells.Fixed(count = columnCount),
    state = lazyGridState,
    contentPadding = PaddingValues(vertical = MeTheme.spacing.sm),
    userScrollEnabled = false,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.sm)
      .heightIn(max = 500.dp),
    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
  ) {
    // Visible metrics
    items(
      items = visibleMetrics,
      key = { stat -> getMetricKey(stat, isVisible = true) },
    ) { metric ->
      val isSelected = selectedStat?.key == metric.key
      ReorderableItem(
        state = reorderableState,
        key = getMetricKey(metric, isVisible = true),
        enabled = inEditMode,
      ) { isDragging ->
        AnimatedStatCard(
          stat = metric,
          isDragging = isDragging,
          inEditMode = inEditMode,
          isSelected = isSelected,
          isFromSetup = isFromSetup,
          modifier = Modifier.longPressDraggableHandle(
            enabled = inEditMode,
            onDragStarted = {
              hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            },
            onDragStopped = {
              hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
            },
          ),

          onBadgeClick = {
            onMetricMoved(true, false, metric)
          },
          onClick = {
            onMetricClick(if (isSelected) null else metric)
          },

          )
      }
    }

    // Hidden metrics (only when in edit mode)
    if (inEditMode) {
      items(
        items = hiddenMetrics,
        key = { stat -> getMetricKey(stat, isVisible = false) },
      ) { metric ->
        AnimatedStatCard(
          stat = metric,
          isVisible = false,
          inEditMode = true,
          isSelected = null,
          isFromSetup = isFromSetup,
          onBadgeClick = {
            onMetricMoved(false, true, metric)
          },
        )
      }
    }
  }
}

/**
 * Generates a unique key for metric items in the grid.
 */
private fun getMetricKey(stat: Stat, isVisible: Boolean): String {
  val prefix = if (isVisible) "visible" else "hidden"
  return when (stat.key) {
    is DashboardKey.Metric -> "$prefix-${stat.key.key.name}"
    is DashboardKey.Milestone -> "$prefix-${stat.key.key.name}"
  }
}

@PreviewTheme
@Composable
private fun DashboardMetricsPreview() {
  MeAppTheme {
    DashboardMetrics(
      metricData = listOf(),
      onMetricClick = {},
    ) {}
  }
}
