package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.DashboardKey
import com.greatergoods.meapp.features.common.helper.StatHelper
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.averageSummary
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable for the dashboard metrics section that displays health metrics in a grid layout.
 */
@Composable
fun DashboardMetrics(
    metricData: List<PeriodBodyScaleSummary>,
    visibleKeys: List<DashboardKey> = listOf(),
    selectedStat: Stat? = null,
    onMetricClick: (Stat?) -> Unit
) {
    // Get the latest summary from day-wise entries for metrics
    val latestSummary = averageSummary(metricData)
    val dashboardMetric = latestSummary?.let { DashboardMetric.fromPeriodSummary(it) } ?: DashboardMetric.empty()
    val metrics =
        StatHelper.getMetrics(dashboardMetric, visibleKeys = visibleKeys, useShort = true, filterNulls = false)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp), // Constrain height
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
        items(
            items = metrics,
            key = { it.key },
        ) { metric ->
            val isSelected = selectedStat?.key == metric.key
            StatCard(
                stat = metric,
                isSelected = isSelected,
                onMetricClick = {
                    if (isSelected) {
                        onMetricClick(null)
                    } else
                        onMetricClick(metric)
                },
            )
        }
        if (metrics.size % 2 != 0) {
            item {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MeTheme.colorScheme.utility,
                    modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
}

@PreviewTheme
@Composable
private fun DashboardMetricsPreview() {
    MeAppTheme {
        DashboardMetrics(
            metricData = listOf(),
        ) {}
    }
}
