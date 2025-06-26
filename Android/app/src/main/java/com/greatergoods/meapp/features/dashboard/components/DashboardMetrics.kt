package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
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
import com.greatergoods.meapp.features.dashboard.string.DashboardString
import com.greatergoods.meapp.features.historyDetail.helper.MetricHelper
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
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
    if (totalItems == 1) {
        MeTheme.colorScheme.primaryBackground
    } else if (index % 2 != 0) {
        MeTheme.colorScheme.primaryBackground
    } else {
        MeTheme.colorScheme.secondaryBackground
    }

    Card(
        modifier = Modifier
            .fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = MeTheme.colorScheme.inverseAction,
        ),
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
                color = MeTheme.colorScheme.textHeading,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
            Text(
                text = metric.label.plus(metric.unit),
                style = MeTheme.typography.subHeading2,
                color = MeTheme.colorScheme.textSubheading,
            )
        }
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
fun List<Double>.averageOrNull(): Double? = if (isNotEmpty()) average().rounded() else null

/**
 * Composable for the dashboard metrics section that displays health metrics in a grid layout.
 */
@Composable
fun DashboardMetrics(metric: List<PeriodBodyScaleSummary>) {
    // Get the latest summary from day-wise entries for metrics
    val latestSummary = averageSummary(metric)
    val dashboardMetric = latestSummary?.let { DashboardMetric.fromPeriodSummary(it) } ?: DashboardMetric.empty()
    val metrics = MetricHelper.getMetrics(dashboardMetric, useShort = true, filterNulls = false)

    if (metrics.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeTheme.colorScheme.primaryBackground),
        ) {
            Text(
                text = DashboardString.Metrics.Title,
                style = MeTheme.typography.heading4,
                color = MeTheme.colorScheme.textHeading,
            )
            Spacer(modifier = Modifier.height(MeTheme.spacing.x3s))
            Text(
                text = "No metrics available",
                style = MeTheme.typography.subHeading2,
                color = MeTheme.colorScheme.textSubheading,
            )
        }
    }


    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
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
            metric = listOf(),
        )
    }
}
