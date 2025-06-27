package com.greatergoods.meapp.features.historyDetail.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.enums.MetricKey
import com.greatergoods.meapp.features.common.strings.MetricLabels
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

object MetricHelper {
    fun getMetrics(
        item: DashboardMetric,
        useShort: Boolean = false,
        filterNulls: Boolean = true,
    ): List<Metric> {
        val metrics = listOf(
            metric(MetricLabels.getLabel(MetricKey.BMI, useShort), item.bmi, "", AppIcons.Metrics.Bmi, MetricKey.BMI),
            metric(
                MetricLabels.getLabel(MetricKey.BODY_FAT, useShort),
                item.bodyFat?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.BodyFat,
                MetricKey.BODY_FAT,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.MUSCLE_MASS, useShort),
                item.muscleMass?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.MuscleMass,
                MetricKey.MUSCLE_MASS,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.BODY_WATER, useShort),
                item.bodyWater?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.Water,
                MetricKey.BODY_WATER,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.HEART_RATE, useShort),
                item.heartRate,
                "bpm",
                AppIcons.Metrics.Pulse,
                MetricKey.HEART_RATE,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.BONE_MASS, useShort),
                item.boneMass?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.BoneMass,
                MetricKey.BONE_MASS,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.VISCERAL_FAT, useShort),
                item.visceralFatLevel,
                "Level",
                AppIcons.Metrics.VisceralFat,
                MetricKey.VISCERAL_FAT,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.SUBCUTANEOUS_FAT, useShort),
                item.subcutaneousFatPercent?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.SubcutaneousFat,
                MetricKey.SUBCUTANEOUS_FAT,

                ),
            metric(
                MetricLabels.getLabel(MetricKey.PROTEIN, useShort),
                item.proteinPercent?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.Protein,
                MetricKey.PROTEIN,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.SKELETAL_MUSCLE, useShort),
                item.skeletalMusclePercent?.toDouble()?.rounded(),
                "%",
                AppIcons.Metrics.MuscleMass,
                MetricKey.SKELETAL_MUSCLE,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.BMR, useShort),
                item.bmr?.toInt(),
                "kcal",
                AppIcons.Metrics.Bmr,
                MetricKey.BMR,
            ),
            metric(
                MetricLabels.getLabel(MetricKey.METABOLIC_AGE, useShort),
                item.metabolicAge?.toInt(),
                "yrs",
                AppIcons.Metrics.MetabolicAge,
                MetricKey.METABOLIC_AGE,
            ),
        )

        return if (filterNulls) metrics.filter { it.value != null } else metrics
    }

    fun metric(label: String, value: Number?, unit: String, icon: Int, metricKey: MetricKey): Metric {
        val calculatedValue = if (value == null || value == 0.0 || value == 0) null else value
        return Metric(
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
