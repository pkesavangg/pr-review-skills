package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.averageSummary
import com.greatergoods.meapp.features.historyDetail.helper.MetricHelper
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Composable for displaying a single metric item in the dashboard metrics grid.
 */
@Composable
private fun MetricItem(
    metric: Metric,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onMetricClick: (Metric) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MeTheme.colorScheme.secondaryAction else MeTheme.colorScheme.inverseAction,
        ),
        onClick = { onMetricClick(metric) },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = MeTheme.spacing.sm),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = metric.value ?: "---",
                style = MeTheme.typography.heading4,
                color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textHeading,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
            Text(
                text = metric.label.plus(metric.unit),
                style = MeTheme.typography.subHeading2,
                color = if (isSelected) MeTheme.colorScheme.inverseAction else MeTheme.colorScheme.textSubheading,
            )
        }
    }
}

/**
 * Composable for the dashboard metrics section that displays health metrics in a grid layout.
 */
@Composable
fun DashboardMetrics(
    metricData: List<PeriodBodyScaleSummary>,
    selectedMetric: Metric? = null,
    onMetricClick: (Metric?) -> Unit
) {
    // Get the latest summary from day-wise entries for metrics
    val latestSummary = averageSummary(metricData)
    val dashboardMetric = latestSummary?.let { DashboardMetric.fromPeriodSummary(it) } ?: DashboardMetric.empty()
    val metrics = MetricHelper.getMetrics(dashboardMetric, useShort = true, filterNulls = false)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MeTheme.spacing.sm)
            .heightIn(max = 500.dp), // Constrain height
        horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
    ) {
        items(
            items = metrics,
            key = { it.key },
        ) { metric ->
            val isSelected = selectedMetric?.key == metric.key
            MetricItem(
                metric = metric,
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
