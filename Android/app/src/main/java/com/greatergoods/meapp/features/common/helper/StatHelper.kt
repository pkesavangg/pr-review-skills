package com.greatergoods.meapp.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.domain.model.storage.entry.DashboardMetric
import com.greatergoods.meapp.features.common.helper.StatMeta.statMetaMap
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.common.strings.MetricLabels
import com.greatergoods.meapp.features.dashboard.strings.DashboardStatsStrings
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeTheme
import kotlin.math.roundToInt

/**
 * Helper for creating Stat objects from DashboardKey and values, and for providing milestone stats.
 */
object StatHelper {
    /**
     * Create a Stat for a given DashboardKey and value. Handles special cases.
     */
    fun DashboardKey.toStat(value: Any?, useShort: Boolean = false): Stat {
        val meta = statMetaMap[this] ?: throw IllegalArgumentException("Unknown DashboardKey: $this")
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
        visibleKeys: List<DashboardKey>? = null,
        useShort: Boolean = false,
        filterNulls: Boolean = true
    ): List<Stat> {
        val keysToUse = visibleKeys?.filter { it.isMetric() } ?: DashboardKey.entries.filter { it.isMetric() }
        return keysToUse.map { key ->
            val value = when (key) {
                DashboardKey.BMI -> item.bmi
                DashboardKey.BODY_FAT -> item.bodyFat?.toDouble()?.rounded()
                DashboardKey.MUSCLE_MASS -> item.muscleMass?.toDouble()?.rounded()
                DashboardKey.BODY_WATER -> item.bodyWater?.toDouble()?.rounded()
                DashboardKey.HEART_RATE -> item.heartRate
                DashboardKey.BONE_MASS -> item.boneMass?.toDouble()?.rounded()
                DashboardKey.VISCERAL_FAT -> item.visceralFatLevel?.roundToInt()
                DashboardKey.SUBCUTANEOUS_FAT -> item.subcutaneousFatPercent?.toDouble()?.rounded()
                DashboardKey.PROTEIN -> item.proteinPercent?.toDouble()?.rounded()
                DashboardKey.SKELETAL_MUSCLE -> item.skeletalMusclePercent?.toDouble()?.rounded()
                DashboardKey.BMR -> item.bmr?.toInt()
                DashboardKey.METABOLIC_AGE -> item.metabolicAge?.toInt()
                else -> null
            }
            key.toStat(value, useShort)
        }.let { metrics ->
            if (filterNulls) metrics.filter { it.value != null } else metrics
        }
    }

    /**
     * Returns a list of milestone Stat objects.
     */
    fun getMilestone(): List<Stat> = listOf(
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

internal object StatMeta {
    /**
     * Metadata for each DashboardKey stat (label, unit, icon).
     */
    val statMetaMap = mapOf(
        DashboardKey.BMI to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.BMI, useShort) },
            unit = "",
            icon = AppIcons.Metrics.Bmi,
        ),
        DashboardKey.BODY_FAT to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.BODY_FAT, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.BodyFat,
        ),
        DashboardKey.MUSCLE_MASS to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.MUSCLE_MASS, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.MuscleMass,
        ),
        DashboardKey.BODY_WATER to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.BODY_WATER, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.Water,
        ),
        DashboardKey.HEART_RATE to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.HEART_RATE, useShort) },
            unit = "bpm",
            unitProvider = { useShort -> if (useShort) null else "bpm" },
            icon = AppIcons.Metrics.Pulse,
        ),
        DashboardKey.BONE_MASS to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.BONE_MASS, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.BoneMass,
        ),
        DashboardKey.SUBCUTANEOUS_FAT to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.SUBCUTANEOUS_FAT, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.SubcutaneousFat,
        ),
        DashboardKey.PROTEIN to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.PROTEIN, useShort) },
            unit = "%",
            icon = AppIcons.Metrics.Protein,
        ),
        DashboardKey.SKELETAL_MUSCLE to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.SKELETAL_MUSCLE, useShort) },
            unit = "%",
            unitProvider = { useShort -> if (useShort) null else "%" },
            icon = AppIcons.Metrics.MuscleMass,
        ),
        DashboardKey.BMR to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.BMR, useShort) },
            unit = "kcal",
            icon = AppIcons.Metrics.Bmr,
        ),
        DashboardKey.METABOLIC_AGE to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.METABOLIC_AGE, useShort) },
            unit = "yrs",
            unitProvider = { useShort -> if (useShort) null else "yrs" },
            icon = AppIcons.Metrics.MetabolicAge,
        ),
        DashboardKey.VISCERAL_FAT to StatMeta(
            labelProvider = { useShort -> MetricLabels.getLabel(DashboardKey.VISCERAL_FAT, useShort) },
            valuePrefix = { useShort -> "Lv." + if (useShort) " " else "" },
            unit = null,
            icon = AppIcons.Metrics.VisceralFat,
        ),
    )

    /**
     * Data class for static stat metadata.
     */
    data class StatMeta(
        val labelProvider: (Boolean) -> String,
        val valuePrefix: (Boolean) -> String = { "" },
        val unit: String? = null,
        val unitProvider: (Boolean) -> String? = { unit }, // default to static unit
        val icon: Int? = null,
    )
}

enum class DashboardKey {
    BMI,
    BODY_FAT,
    MUSCLE_MASS,
    BODY_WATER,
    HEART_RATE,
    BONE_MASS,
    VISCERAL_FAT,
    SUBCUTANEOUS_FAT,
    PROTEIN,
    SKELETAL_MUSCLE,
    BMR,
    METABOLIC_AGE,
    CURRENT_STREAK,
    LONGEST_STREAK,
    PER_WEEK,
    PER_MONTH,
    PER_YEAR,
    TOTAL_CHANGE,
    TO_GOAL
}

