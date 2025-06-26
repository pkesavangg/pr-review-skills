package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.dashboard.string.DashboardString
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
    modifier: Modifier = Modifier,
    index: Int,
    totalItems: Int,
) {
    val backgroundColor = if (totalItems == 1) {
        MeTheme.colorScheme.primaryBackground
    } else if (index % 2 != 0) {
        MeTheme.colorScheme.primaryBackground
    } else {
        MeTheme.colorScheme.secondaryBackground
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(all = MeTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = metric.label,
                style = MeTheme.typography.body2,
                color = MeTheme.colorScheme.textBody,
            )
            AppIcon(
                id = metric.icon,
                contentDescription = metric.label,
            )
        }

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        Text(
            text = metric.value.plus(metric.unit),
            style = MeTheme.typography.heading3,
            color = MeTheme.colorScheme.textHeading,
        )
    }
}

fun averageSummary(metrics: List<PeriodBodyScaleSummary>): PeriodBodyScaleSummary? {
    if (metrics.isEmpty()) return null

    val size = metrics.size.toDouble()

    return PeriodBodyScaleSummary(
        period = "average",
        entryTimestamp = metrics.maxByOrNull { it.entryTimestamp }?.entryTimestamp.orEmpty(),
        weight = metrics.sumOf { it.weight } / size,
        bodyFat = metrics.mapNotNull { it.bodyFat }.averageOrNull(),
        muscleMass = metrics.mapNotNull { it.muscleMass }.averageOrNull(),
        water = metrics.mapNotNull { it.water }.averageOrNull(),
        bmi = metrics.mapNotNull { it.bmi }.averageOrNull(),
        bmr = metrics.mapNotNull { it.bmr }.averageOrNull(),
        metabolicAge = metrics.mapNotNull { it.metabolicAge }.averageOrNull(),
        proteinPercent = metrics.mapNotNull { it.proteinPercent }.averageOrNull(),
        pulse = metrics.mapNotNull { it.pulse }.averageOrNull(),
        skeletalMusclePercent = metrics.mapNotNull { it.skeletalMusclePercent }.averageOrNull(),
        subcutaneousFatPercent = metrics.mapNotNull { it.subcutaneousFatPercent }.averageOrNull(),
        visceralFatLevel = metrics.mapNotNull { it.visceralFatLevel }.averageOrNull(),
        boneMass = metrics.mapNotNull { it.boneMass }.averageOrNull(),
        impedance = metrics.mapNotNull { it.impedance }.averageOrNull(),
        unit = metrics.firstOrNull { it.unit != null }?.unit,
    )
}

// Extension for safe nullable average
fun List<Double>.averageOrNull(): Double? = if (isNotEmpty()) average() else null

/**
 * Composable for the dashboard metrics section that displays health metrics in a grid layout.
 */
@Composable
fun DashboardMetrics(metric: List<PeriodBodyScaleSummary>) {
    // Get the latest summary from day-wise entries for metrics
    val latestSummary = averageSummary(metric)
    val dashboardMetric = latestSummary?.let { DashboardMetric.fromPeriodSummary(it) } ?: DashboardMetric.empty()
    val metrics = MetricHelper.getMetrics(dashboardMetric)

    if (metrics.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeTheme.colorScheme.primaryBackground)
                .padding(MeTheme.spacing.md),
        ) {
            Text(
                text = DashboardString.Metrics.Title,
                style = MeTheme.typography.heading4,
                color = MeTheme.colorScheme.textHeading,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
            Text(
                text = "No metrics available",
                style = MeTheme.typography.body2,
                color = MeTheme.colorScheme.textSubheading,
            )
        }
        return
    }



    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp), // Constrain height
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(
            items = metrics,
            key = { it.label },
        ) { metric ->
            MetricItem(
                metric = metric,
                index = metrics.indexOf(metric),
                totalItems = metrics.size,
            )
        }
        if (metrics.size % 2 != 0) {
            item {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MeTheme.colorScheme.utility,
                    modifier = Modifier.padding(horizontal = MeTheme.spacing.md),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
}

@PreviewTheme
@Composable
private fun DashboardMetricsPreview() {
    MeAppTheme {
        DashboardMetrics(
            metric = listOf(),
        )
    }
}
