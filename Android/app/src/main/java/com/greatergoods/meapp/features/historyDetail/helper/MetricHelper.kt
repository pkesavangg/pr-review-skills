package com.greatergoods.meapp.features.historyDetail.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.strings.MetricLabels
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.proto.DashboardKey
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

object MetricHelper {
    fun getMetrics(
        item: DashboardMetric,
        visibleKeys: List<DashboardKey>? = null, // 👈 nullable default
        useShort: Boolean = false,
        filterNulls: Boolean = true,
    ): List<Metric> {
        val keysToUse = visibleKeys
            ?.filter { it.isMetric() }
            ?: DashboardKey.entries.filter { it.isMetric() }

        return keysToUse.mapNotNull { key ->
            when (key) {
                DashboardKey.BMI -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.bmi,
                    "",
                    AppIcons.Metrics.Bmi,
                    key,
                )

                DashboardKey.BODY_FAT -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.bodyFat?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.BodyFat,
                    key,
                )

                DashboardKey.MUSCLE_MASS -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.muscleMass?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.MuscleMass,
                    key,
                )

                DashboardKey.BODY_WATER -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.bodyWater?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.Water,
                    key,
                )

                DashboardKey.HEART_RATE -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.heartRate,
                    "bpm",
                    AppIcons.Metrics.Pulse,
                    key,
                )

                DashboardKey.BONE_MASS -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.boneMass?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.BoneMass,
                    key,
                )

                DashboardKey.VISCERAL_FAT -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.visceralFatLevel,
                    "Level",
                    AppIcons.Metrics.VisceralFat,
                    key,
                )

                DashboardKey.SUBCUTANEOUS_FAT -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.subcutaneousFatPercent?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.SubcutaneousFat,
                    key,
                )

                DashboardKey.PROTEIN -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.proteinPercent?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.Protein,
                    key,
                )

                DashboardKey.SKELETAL_MUSCLE -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.skeletalMusclePercent?.toDouble()?.rounded(),
                    "%",
                    AppIcons.Metrics.MuscleMass,
                    key,
                )

                DashboardKey.BMR -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.bmr?.toInt(),
                    "kcal",
                    AppIcons.Metrics.Bmr,
                    key,
                )

                DashboardKey.METABOLIC_AGE -> metric(
                    MetricLabels.getLabel(key, useShort),
                    item.metabolicAge?.toInt(),
                    "yrs",
                    AppIcons.Metrics.MetabolicAge,
                    key,
                )

                else -> null
            }
        }.let { metrics ->
            if (filterNulls) metrics.filter { it.value != null } else metrics
        }
    }

    fun metric(label: String, value: Number?, unit: String, icon: Int, dashboardKey: DashboardKey): Metric {
        val calculatedValue = if (value == null || value == 0.0 || value == 0) null else value
        return Metric(
            label = label,
            value = calculatedValue?.toString(),
            unit = unit,
            icon = icon,
            key = dashboardKey,
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

    fun DashboardKey.isMetric(): Boolean = when (this) {
        DashboardKey.BMI,
        DashboardKey.BODY_FAT,
        DashboardKey.MUSCLE_MASS,
        DashboardKey.BODY_WATER,
        DashboardKey.HEART_RATE,
        DashboardKey.BONE_MASS,
        DashboardKey.VISCERAL_FAT,
        DashboardKey.SUBCUTANEOUS_FAT,
        DashboardKey.PROTEIN,
        DashboardKey.SKELETAL_MUSCLE,
        DashboardKey.BMR,
        DashboardKey.METABOLIC_AGE -> true

        else -> false
    }

    fun DashboardKey.isStat(): Boolean = when (this) {
        DashboardKey.TO_GOAL,
        DashboardKey.CURRENT_STREAK,
        DashboardKey.LONGEST_STREAK,
        DashboardKey.PER_WEEK,
        DashboardKey.PER_MONTH,
        DashboardKey.PER_YEAR,
        DashboardKey.TOTAL_CHANGE -> true

        else -> false
    }
}
