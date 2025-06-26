package com.greatergoods.meapp.features.historyDetail.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.features.historyDetail.strings.HistoryDetailScreenStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

object MetricHelper {
    fun getMetrics(item: DashboardMetric): List<Metric> {
        return listOfNotNull(
            metric(HistoryDetailScreenStrings.BmiLabel, item.bmi, "", AppIcons.Metrics.Bmi),
            metric(
                HistoryDetailScreenStrings.BodyFatLabel,
                item.bodyFat,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.BodyFat,
            ),
            metric(
                HistoryDetailScreenStrings.MuscleMassLabel,
                item.muscleMass,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.MuscleMass,
            ),
            metric(
                HistoryDetailScreenStrings.BodyWaterLabel,
                item.bodyWater,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.Water,
            ),
            metric(
                HistoryDetailScreenStrings.HeartRateLabel,
                item.heartRate,
                HistoryDetailScreenStrings.BpmUnit,
                AppIcons.Metrics.Pulse,
            ),
            metric(
                HistoryDetailScreenStrings.BoneMassLabel,
                item.boneMass,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.BoneMass,
            ),
            metric(
                HistoryDetailScreenStrings.VisceralFatLabel,
                item.visceralFatLevel,
                HistoryDetailScreenStrings.LevelUnit,
                AppIcons.Metrics.VisceralFat,
            ),
            metric(
                HistoryDetailScreenStrings.SubcutaneousFatLabel,
                item.subcutaneousFatPercent,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.SubcutaneousFat,
            ),
            metric(
                HistoryDetailScreenStrings.ProteinLabel,
                item.proteinPercent,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.Protein,
            ),
            metric(
                HistoryDetailScreenStrings.SkeletalMuscleLabel,
                item.skeletalMusclePercent,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.MuscleMass,
            ),
            metric(
                HistoryDetailScreenStrings.BmrLabel,
                item.bmr?.toInt(),
                HistoryDetailScreenStrings.KcalUnit,
                AppIcons.Metrics.Bmr,
            ),
            metric(
                HistoryDetailScreenStrings.MetabolicAgeLabel,
                item.metabolicAge?.toInt(),
                HistoryDetailScreenStrings.YearsUnit,
                AppIcons.Metrics.MetabolicAge,
            ),
        )
    }

    fun metric(label: String, value: Number?, unit: String, icon: Int): Metric? {
        if (value == null || value == 0 || value == 0.0) return null
        return Metric(
            label = label,
            value = value.toString(),
            unit = unit,
            icon = icon,
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
