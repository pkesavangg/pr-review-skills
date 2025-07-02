package com.greatergoods.meapp.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.helper.StatMeta.metricStatMetaMap
import com.greatergoods.meapp.features.common.helper.StatMeta.milestoneStatMetaMap
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.common.strings.MetricLabels
import com.greatergoods.meapp.features.dashboard.string.DashboardString
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.proto.MetricKey
import com.greatergoods.meapp.proto.MilestoneKey
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme
import kotlin.math.roundToInt

/**
 * Helper for creating Stat objects from MetricKey and values, and for providing milestone stats.
 */
object StatHelper {
    /**
     * Create a Stat for a given DashboardKey and value. Handles special cases.
     */
    fun DashboardKey.toStat(value: Any?, useShort: Boolean = false): Stat {
        val meta = when (this) {
            is DashboardKey.Metric -> {
                metricStatMetaMap[key] ?: throw IllegalArgumentException("Unknown MetricKey: $key")
            }

            is DashboardKey.Milestone -> {
                milestoneStatMetaMap[key] ?: throw IllegalArgumentException("Unknown MilestoneKey: $key")
            }
        }

        val valueStr = when (value) {
            is Number -> if (value == 0.0 || value == 0) null else value.toString()
            is String -> if (value.isBlank() || value == "0") null else value
            else -> null
        }
        val prefix = meta.valuePrefix(useShort).takeIf { it.isNotEmpty() }
        return Stat(
            label = meta.labelProvider(useShort),
            value = valueStr,
            unit = meta.unitProvider(useShort),
            icon = if (!useShort) meta.icon else null,
            key = this,
            valuePrefix = prefix,
        )
    }

    /**
     * Returns a list of Stat objects for all metric keys in DashboardMetric.
     */
    fun getMetrics(
        item: DashboardMetric,
        visibleKeys: List<MetricKey>? = null,
        useShort: Boolean = false,
        filterNulls: Boolean = true
    ): List<Stat> {
        val keysToUse = (visibleKeys ?: MetricKey.entries).filter { it != MetricKey.UNRECOGNIZED }
        return keysToUse.map { key ->
            val value = when (key) {
                MetricKey.BMI -> item.bmi
                MetricKey.BODY_FAT -> item.bodyFat?.toDouble()?.rounded()
                MetricKey.MUSCLE_MASS -> item.muscleMass?.toDouble()?.rounded()
                MetricKey.BODY_WATER -> item.bodyWater?.toDouble()?.rounded()
                MetricKey.HEART_RATE -> item.heartRate
                MetricKey.BONE_MASS -> item.boneMass?.toDouble()?.rounded()
                MetricKey.VISCERAL_FAT -> item.visceralFatLevel?.roundToInt()
                MetricKey.SUBCUTANEOUS_FAT -> item.subcutaneousFatPercent?.toDouble()?.rounded()
                MetricKey.PROTEIN -> item.proteinPercent?.toDouble()?.rounded()
                MetricKey.SKELETAL_MUSCLE -> item.skeletalMusclePercent?.toDouble()?.rounded()
                MetricKey.BMR -> item.bmr?.toInt()
                MetricKey.METABOLIC_AGE -> item.metabolicAge?.toInt()
                else -> null
            }
            DashboardKey.Metric(key).toStat(value, useShort)
        }.let { metrics ->
            if (filterNulls) metrics.filter { it.value != null } else metrics
        }
    }

    private val milestoneValues = mapOf(
        MilestoneKey.CURRENT_STREAK to "1 day",
        MilestoneKey.LONGEST_STREAK to "1 day",
        MilestoneKey.PER_WEEK to "!",
        MilestoneKey.PER_MONTH to "!",
        MilestoneKey.PER_YEAR to "1",
        MilestoneKey.TOTAL_CHANGE to "1",
    )

    /**
     * Returns a list of milestone Stat objects.
     */
    fun getMilestone(
        visibleKeys: List<MilestoneKey>? = null,
        useShort: Boolean = false,
        filterNulls: Boolean = true
    ): List<Stat> {
        val keysToUse = (visibleKeys ?: MilestoneKey.entries)
            .filter { it != MilestoneKey.UNRECOGNIZED && it != MilestoneKey.TO_GOAL }

        val stats = keysToUse.map { key ->
            val value = milestoneValues[key] ?: ""
            DashboardKey.Milestone(key).toStat(value, useShort)
        }

        return if (filterNulls) stats.filter { it.value != null } else stats
    }

    /**
     * Returns a background color for a stat card based on its index and total size.
     */
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

internal object StatMeta {
    /**
     * Metadata for each MetricKey stat (label, unit, icon).
     */
    val metricStatMetaMap = mapOf(
        MetricKey.BMI to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.BMI, useShort) },
            unit = "",
            icon = AppIcons.Metrics.Bmi,
        ),
        MetricKey.BODY_FAT to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.BODY_FAT, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.BodyFat,
        ),
        MetricKey.MUSCLE_MASS to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.MUSCLE_MASS, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.MuscleMass,
        ),
        MetricKey.BODY_WATER to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.BODY_WATER, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.Water,
        ),
        MetricKey.HEART_RATE to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.HEART_RATE, useShort) },
            unit = "bpm",
            unitProvider = { useShort -> if (useShort) null else "bpm" },
            icon = AppIcons.Metrics.Pulse,
        ),
        MetricKey.BONE_MASS to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.BONE_MASS, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.BoneMass,
        ),
        MetricKey.SUBCUTANEOUS_FAT to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.SUBCUTANEOUS_FAT, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.SubcutaneousFat,
        ),
        MetricKey.PROTEIN to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.PROTEIN, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.Protein,
        ),
        MetricKey.SKELETAL_MUSCLE to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.SKELETAL_MUSCLE, useShort) },
            unit = "%",
            unitProvider = { useShort -> if (useShort) null else "%" },
            icon = AppIcons.Metrics.MuscleMass,
        ),
        MetricKey.BMR to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.BMR, useShort) },
            unit = "kcal",
            icon = AppIcons.Metrics.Bmr,
        ),
        MetricKey.METABOLIC_AGE to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.METABOLIC_AGE, useShort) },
            unit = "yrs",
            unitProvider = { useShort -> if (useShort) null else "yrs" },
            icon = AppIcons.Metrics.MetabolicAge,
        ),
        MetricKey.VISCERAL_FAT to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.VISCERAL_FAT, useShort) },
            valuePrefix = { useShort -> "Lv." + if (useShort) " " else "" },
            unit = null,
            icon = AppIcons.Metrics.VisceralFat,
        ),
    )

    val milestoneStatMetaMap: Map<MilestoneKey, StatMeta> = mapOf(
        MilestoneKey.CURRENT_STREAK to StatMeta(
            labelProvider = { _ -> DashboardString.MileStone.CurrentStreak },
            icon = AppIcons.Milestone.Bolt,
            unit = "day",
        ),
        MilestoneKey.LONGEST_STREAK to StatMeta(
            labelProvider = { _ -> DashboardString.MileStone.LongestStreak },
            icon = AppIcons.Milestone.Streak,
            unit = "day",
        ),
        MilestoneKey.PER_WEEK to StatMeta(
            labelProvider = { _ -> DashboardString.MileStone.LbsPerWeek },
            unit = "lbs/week",
        ),
        MilestoneKey.PER_MONTH to StatMeta(
            labelProvider = { _ -> DashboardString.MileStone.LbsPerMonth },
            unit = "lbs/month",
        ),
        MilestoneKey.PER_YEAR to StatMeta(
            labelProvider = { _ -> DashboardString.MileStone.LbsPerYear },
            unit = "lbs/year",
        ),
        MilestoneKey.TOTAL_CHANGE to StatMeta(
            labelProvider = { _ -> DashboardString.MileStone.LbsTotal },
            unit = "lbs",
        ),
    )

    /**
     * Data class for static stat metadata.
     */
    data class StatMeta(
        val labelProvider: (Boolean) -> String,
        val unit: String? = null,
        val unitProvider: (Boolean) -> String? = { _ -> unit },
        val valuePrefix: (Boolean) -> String = { _ -> "" },
        val icon: Int? = null,
    )
}
