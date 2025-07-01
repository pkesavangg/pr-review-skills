package com.greatergoods.meapp.features.historyDetail.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.enums.MetricKey
import com.greatergoods.meapp.features.common.strings.MetricLabels
import com.greatergoods.meapp.features.dashboard.strings.DashboardStatsStrings
import com.greatergoods.meapp.features.historyDetail.modal.Stat
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

object MetricHelper {
    fun getMetrics(
        item: DashboardMetric,
        useShort: Boolean = false,
        filterNulls: Boolean = true,
    ): List<Stat> {
        val keysToUse = visibleKeys
            ?.filter { it.isMetric() }
            ?: DashboardKey.entries.filter { it.isMetric() }

        return keysToUse.mapNotNull { key ->
            when (key) {
                DashboardKey.BMI -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.bmi,
                    "",
                    AppIcons.Metrics.Bmi,
                    key,
                )

                DashboardKey.BODY_FAT -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.bodyFat?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.BodyFat,
                    key,
                )

                DashboardKey.MUSCLE_MASS -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.muscleMass?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.MuscleMass,
                    key,
                )

                DashboardKey.BODY_WATER -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.bodyWater?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.Water,
                    key,
                )

                DashboardKey.HEART_RATE -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.heartRate,
                    "bpm",
                    AppIcons.Metrics.Pulse,
                    key,
                )

                DashboardKey.BONE_MASS -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.boneMass?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.BoneMass,
                    key,
                )

                DashboardKey.VISCERAL_FAT -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.visceralFatLevel,
                    "Level",
                    AppIcons.Metrics.VisceralFat,
                    key,
                )

                DashboardKey.SUBCUTANEOUS_FAT -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.subcutaneousFatPercent?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.SubcutaneousFat,
                    key,
                )

                DashboardKey.PROTEIN -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.proteinPercent?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.Protein,
                    key,
                )

                DashboardKey.SKELETAL_MUSCLE -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.skeletalMusclePercent?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.MuscleMass,
                    key,
                )

                DashboardKey.BMR -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.bmr?.toInt(),
                    "kcal",
                    AppIcons.Metrics.Bmr,
                    key,
                )

                DashboardKey.METABOLIC_AGE -> stat(
                    MetricLabels.getLabel(key, useShort),
                    item.metabolicAge?.toInt(),
                    "yrs",
                    AppIcons.Metrics.MetabolicAge,
                    key,
                )

                else -> null
            }
        }.let { metrics ->
            if (filterNulls) metrics.filter { it.value != null } else metrics.map { it.copy(icon = null) }
        }
    }

    fun getMilestone(): List<Stat> {
        return listOf(
            Stat(
                label = DashboardStatsStrings.CurrentStreak,
                value = "1 day",
                icon = AppIcons.Milestone.Bolt,
                key = DashboardKey.CURRENT_STREAK,
            ),
            Stat(
                label = DashboardStatsStrings.LongestStreak,
                value = "1 day",
                icon = AppIcons.Milestone.Streak,
                key = DashboardKey.LONGEST_STREAK,
            ),
            Stat(
                label = DashboardStatsStrings.LbsPerWeek,
                value = "!",
                key = DashboardKey.PER_WEEK,
            ),
            Stat(
                label = DashboardStatsStrings.LbsPerMonth,
                value = "!",
                key = DashboardKey.PER_MONTH,
            ),
            Stat(
                label = DashboardStatsStrings.LbsPerYear,
                value = "1",
                key = DashboardKey.PER_YEAR,
            ),
            Stat(
                label = DashboardStatsStrings.LbsTotal,
                value = "1",
                key = DashboardKey.TOTAL_CHANGE,
            ),
        )
    }

    fun stat(label: String, value: Number?, unit: String, icon: Int, dashboardKey: DashboardKey): Stat {
        val calculatedValue = if (value == null || value == 0.0 || value == 0) null else value
        return Stat(
            label = label,
            value = calculatedValue?.toString(),
            unit = unit,
            icon = icon,
            key = metricKey,
        )
    }

    @Composable
    fun getBgColor(index: Int, size: Int): Color {
        return if (size == 1)
            MeTheme.colorScheme.primaryBackground
        else if (index % 2 != 0)
            MeTheme.colorScheme.primaryBackground
        else
            MeTheme.colorScheme.secondaryBackground
    }
}
