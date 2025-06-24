package com.greatergoods.meapp.features.historyDetail.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.features.historyDetail.modal.Metric
import com.greatergoods.meapp.features.historyDetail.strings.HistoryDetailScreenStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme

object MetricHelper {
    fun getMetrics(item: ScaleEntry): List<Metric> {
        val scale = item.scale.scaleEntry
        val metric = item.scale.scaleEntryMetric
        return listOfNotNull(
            metric(HistoryDetailScreenStrings.BmiLabel, scale.bmi, "", AppIcons.Metrics.Bmi),
            metric(
                HistoryDetailScreenStrings.BodyFatLabel,
                scale.bodyFat,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.BodyFat,
            ),
            metric(
                HistoryDetailScreenStrings.MuscleMassLabel,
                scale.muscleMass,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.MuscleMass,
            ),
            metric(
                HistoryDetailScreenStrings.BodyWaterLabel,
                scale.water,
                HistoryDetailScreenStrings.PercentageUnit,
                AppIcons.Metrics.Water,
            ),
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.HeartRateLabel,
                    it.pulse,
                    HistoryDetailScreenStrings.BpmUnit,
                    AppIcons.Metrics.Pulse,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.BoneMassLabel,
                    it.boneMass,
                    HistoryDetailScreenStrings.PercentageUnit,
                    AppIcons.Metrics.BoneMass,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.VisceralFatLabel,
                    it.visceralFatLevel,
                    HistoryDetailScreenStrings.LevelUnit,
                    AppIcons.Metrics.VisceralFat,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.SubcutaneousFatLabel,
                    it.subcutaneousFatPercent,
                    HistoryDetailScreenStrings.PercentageUnit,
                    AppIcons.Metrics.SubcutaneousFat,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.ProteinLabel,
                    it.proteinPercent,
                    HistoryDetailScreenStrings.PercentageUnit,
                    AppIcons.Metrics.Protein,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.SkeletalMuscleLabel,
                    it.skeletalMusclePercent,
                    HistoryDetailScreenStrings.PercentageUnit,
                    AppIcons.Metrics.MuscleMass,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.BmrLabel,
                    it.bmr.toInt(),
                    HistoryDetailScreenStrings.KcalUnit,
                    AppIcons.Metrics.Bmr,
                )
            },
            metric?.let {
                metric(
                    HistoryDetailScreenStrings.MetabolicAgeLabel,
                    it.metabolicAge?.toInt(),
                    HistoryDetailScreenStrings.YearsUnit,
                    AppIcons.Metrics.MetabolicAge,
                )
            },
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
