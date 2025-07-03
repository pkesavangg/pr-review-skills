package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.StatHelper
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.averageSummary
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable for the dashboard metrics section that displays health metrics in a grid layout.
 *
 * @param metricData List of period body scale summaries for calculating metrics
 * @param inEditMode Whether the dashboard is in edit mode
 * @param visibleKeys List of currently visible dashboard keys
 * @param selectedStat Currently selected stat for highlighting
 * @param onMetricClick Callback when a metric is clicked
 * @param onMetricsChanged Callback when visible metrics are changed (for save functionality)
 */
@Composable
fun DashboardMetrics(
    metricData: List<PeriodBodyScaleSummary>,
    inEditMode: Boolean = false,
    visibleKeys: List<DashboardKey> = listOf(),
    selectedStat: Stat? = null,
    onMetricClick: (Stat?) -> Unit,
    onMetricsChanged: (List<DashboardKey>) -> Unit = { }
) {
    var localVisibleKeys by remember(visibleKeys) { mutableStateOf(visibleKeys) }

    val latestSummary = averageSummary(metricData)
    val dashboardMetric = latestSummary?.let { DashboardMetric.fromPeriodSummary(it) } ?: DashboardMetric.empty()
    val metricKeys = localVisibleKeys.mapNotNull { key ->
        when (key) {
            is DashboardKey.Metric -> key.key
            is DashboardKey.Milestone -> null
        }
    }
    val visibleMetrics = StatHelper.getMetrics(
        dashboardMetric,
        visibleKeys = metricKeys,
        useShort = true,
        filterNulls = false,
    )
    val allMetrics = StatHelper.getMetrics(
        dashboardMetric,
        visibleKeys = null,
        useShort = true,
        filterNulls = true,
    )
    val hiddenMetrics = allMetrics.filter { it !in visibleMetrics }

    val onMetricMoved = { fromVisible: Boolean, toVisible: Boolean, metric: Stat ->
        val metricKey = metric.key
        if (fromVisible && !toVisible) {
            val newKeys = localVisibleKeys.filterNot { it == metricKey }
            localVisibleKeys = newKeys
            onMetricsChanged(newKeys)
        } else if (!fromVisible && toVisible) {
            val newKeys = localVisibleKeys + metricKey
            localVisibleKeys = newKeys
            onMetricsChanged(newKeys)
        }
    }

    DashboardMetricsGrid(
        visibleMetrics = visibleMetrics,
        hiddenMetrics = hiddenMetrics,
        inEditMode = inEditMode,
        selectedStat = selectedStat,
        onMetricClick = onMetricClick,
        onMetricMoved = onMetricMoved,
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
}

/**
 * Grid layout for displaying dashboard metrics.
 */
@Composable
private fun DashboardMetricsGrid(
    visibleMetrics: List<Stat>,
    hiddenMetrics: List<Stat>,
    inEditMode: Boolean,
    selectedStat: Stat?,
    onMetricClick: (Stat?) -> Unit,
    onMetricMoved: (fromVisible: Boolean, toVisible: Boolean, metric: Stat) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(MeTheme.spacing.sm),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
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
            AnimatedStatCard(
                stat = metric,
                inEditMode = inEditMode,
                isSelected = isSelected,
                onBadgeClick = {
                    onMetricMoved(true, false, metric)
                },
                onClick = {
                    onMetricClick(if (isSelected) null else metric)
                },
            )
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
