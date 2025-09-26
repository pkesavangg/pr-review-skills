package com.dmdbrands.gurus.weight.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.helper.StatMeta.metricStatMetaMap
import com.dmdbrands.gurus.weight.features.common.helper.StatMeta.milestoneStatMetaMap
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.common.strings.MetricLabels
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded
import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlin.math.roundToInt

/**
 * Helper for creating Stat objects from MetricKey and values, and for providing milestone stats.
 */
object StatHelper {
  /**
   * Returns the value for a given MetricKey from a DashboardMetric.
   */

  fun MetricKey.toStringKey(): String {
    return when (this) {
      MetricKey.BMI -> "bmi"
      MetricKey.BODY_FAT -> "bodyFat"
      MetricKey.MUSCLE_MASS -> "muscleMass"
      MetricKey.BODY_WATER -> "water"
      MetricKey.BONE_MASS -> "boneMass"
      MetricKey.VISCERAL_FAT -> "visceralFatLevel"
      MetricKey.SUBCUTANEOUS_FAT -> "subcutaneousFatPercent"
      MetricKey.PROTEIN -> "proteinPercent"
      MetricKey.SKELETAL_MUSCLE -> "skeletalMusclePercent"
      MetricKey.HEART_RATE -> "pulse"
      MetricKey.BMR -> "bmr"
      MetricKey.METABOLIC_AGE -> "metabolicAge"
      else -> ""
    }
  }

  /**
   * Extension function to convert string to MetricKey.
   */
  fun String.toMetricKey(): MetricKey? {
    return when (this) {
      "bmi" -> MetricKey.BMI
      "bodyFat" -> MetricKey.BODY_FAT
      "muscleMass" -> MetricKey.MUSCLE_MASS
      "water" -> MetricKey.BODY_WATER
      "boneMass" -> MetricKey.BONE_MASS
      "visceralFatLevel" -> MetricKey.VISCERAL_FAT
      "subcutaneousFatPercent" -> MetricKey.SUBCUTANEOUS_FAT
      "proteinPercent" -> MetricKey.PROTEIN
      "skeletalMusclePercent" -> MetricKey.SKELETAL_MUSCLE
      "pulse" -> MetricKey.HEART_RATE
      "bmr" -> MetricKey.BMR
      "metabolicAge" -> MetricKey.METABOLIC_AGE
      else -> null
    }
  }

  fun getMetricValue(item: DashboardMetric, key: MetricKey, useShort: Boolean = false): Stat {
    val value = when (key) {
      MetricKey.WEIGHT -> item.weight
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
    return DashboardKey.Metric(key).toStat(value, useShort, item.unit)
  }

  /**
   * Create a Stat for a given DashboardKey and value. Handles special cases.
   */
  fun DashboardKey.toStat(value: Any?, useShort: Boolean = false, unit: WeightUnit = WeightUnit.LB): Stat {
    val meta = when (this) {
      is DashboardKey.Metric -> {
        metricStatMetaMap[key] ?: throw IllegalArgumentException("Unknown MetricKey: $key")
      }

      is DashboardKey.Milestone -> {
        milestoneStatMetaMap[key]
          ?: throw IllegalArgumentException("Unknown MilestoneKey: $key")
      }
    }

    val valueStr = when (value) {
      is Number -> if (value == 0.0 || value == 0) null else value.toString()
      is String -> if (value.isBlank() || value == "0") null else value
      else -> null
    }
    val prefix = meta.valuePrefix(useShort).takeIf { it.isNotEmpty() }
    val suffix = meta.valueSuffix(useShort).takeIf { it.isNotEmpty() }
    val calculatedUnit = if (this is DashboardKey.Metric && (this.key == MetricKey.WEIGHT)) {
      unit.label
    } else {
      meta.unitProvider(useShort)
    }
    return Stat(
      label = meta.labelProvider(useShort),
      value = valueStr,
      unit = calculatedUnit,
      icon = if (!useShort) meta.icon else null,
      key = this,
      valuePrefix = prefix,
      valueSuffix = suffix,
    )
  }

  /**
   * Returns a list of Stat objects for all metric keys in DashboardMetric.
   */
  fun getMetrics(
    item: DashboardMetric? = null,
    visibleKeys: List<MetricKey>? = null,
    filterWeight: Boolean = true,
    useShort: Boolean = false,
    filterNulls: Boolean = true
  ): List<Stat> {
    // Pre-filter keys to avoid repeated filtering
    val keysToUse = (visibleKeys ?: MetricKey.entries).filter { key ->
      key != MetricKey.UNRECOGNIZED && (!filterWeight || key != MetricKey.WEIGHT)
    }

    // Use buildList for better performance with large datasets
    return buildList {
      for (key in keysToUse) {
        val stat = if (item != null) {
          getMetricValue(item, key, useShort)
        } else {
          DashboardKey.Metric(key).toStat(null, useShort)
        }

        // Only add if not filtering nulls or if value is not null
        if (!filterNulls || stat.value != null) {
          add(stat)
        }
      }
    }
  }

  /**
   * Returns a list of milestone Stat objects.
   */
  fun getMilestone(
    progress: Progress,
    visibleKeys: List<MilestoneKey>? = null,
    useShort: Boolean = false,
    filterNulls: Boolean = true,
  ): List<Stat> {
    val keysToUse = (visibleKeys ?: MilestoneKey.entries)
      .filter { it != MilestoneKey.UNRECOGNIZED }

    val stats = keysToUse.map { key ->
      val value = getMilestoneValue(progress, key)
      DashboardKey.Milestone(key).toStat(value, useShort)
    }

    return if (filterNulls) stats.filter { it.value != null } else stats
  }

  /**
   * Extracts milestone values from Progress object based on MilestoneKey.
   */
  private fun getMilestoneValue(
    progress: Progress,
    key: MilestoneKey,
  ): Any? {
    return when (key) {
      MilestoneKey.CURRENT_STREAK -> progress.currentStreak.takeIf { it > 0 }
      MilestoneKey.LONGEST_STREAK -> progress.longestStreak.takeIf { it > 0 }
      MilestoneKey.PER_WEEK -> progress.week.takeIf { it != 0.0 }?.let {
        if (it > 0) "+${it.rounded()}" else it.rounded().toString()
      }

      MilestoneKey.PER_MONTH -> progress.month.takeIf { it != 0.0 }?.let {
        if (it > 0) "+${it.rounded()}" else it.rounded().toString()
      }

      MilestoneKey.PER_YEAR -> progress.year.takeIf { it != 0.0 }?.let {
        if (it > 0) "+${it.rounded()}" else it.rounded().toString()
      }

      MilestoneKey.TOTAL_CHANGE -> progress.total.takeIf { it != 0.0 }?.let {
        if (it > 0) "+${it.rounded()}" else it.rounded().toString()
      }

      else -> null
    }
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
    MetricKey.WEIGHT to StatMeta(
      labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.WEIGHT, useShort) },
    ),
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
      labelProvider = { useShort ->
        MetricLabels.getLabel(
          MetricKey.SUBCUTANEOUS_FAT,
          useShort,
        )
      },
      unit = "%",
      icon = AppIcons.Metrics.SubcutaneousFat,
    ),
    MetricKey.PROTEIN to StatMeta(
      labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.PROTEIN, useShort) },
      unit = "%",
      icon = AppIcons.Metrics.Protein,
    ),
    MetricKey.SKELETAL_MUSCLE to StatMeta(
      labelProvider = { useShort ->
        MetricLabels.getLabel(
          MetricKey.SKELETAL_MUSCLE,
          useShort,
        )
      },
      unit = "%",
      unitProvider = { useShort -> if (useShort) null else "%" },
      icon = AppIcons.Metrics.SkeletalMusclePercent,
    ),
    MetricKey.BMR to StatMeta(
      labelProvider = { useShort -> MetricLabels.getLabel(MetricKey.BMR, useShort) },
      unit = "kcal",
      icon = AppIcons.Metrics.Bmr,
    ),
    MetricKey.METABOLIC_AGE to StatMeta(
      labelProvider = { useShort ->
        MetricLabels.getLabel(
          MetricKey.METABOLIC_AGE,
          useShort,
        )
      },
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
      valueSuffix = { "day" },
      icon = AppIcons.Milestone.Bolt,
    ),
    MilestoneKey.LONGEST_STREAK to StatMeta(
      labelProvider = { _ -> DashboardString.MileStone.LongestStreak },
      valueSuffix = { "day" },
      icon = AppIcons.Milestone.Streak,
    ),
    MilestoneKey.PER_WEEK to StatMeta(
      labelProvider = { _ -> DashboardString.MileStone.LbsPerWeek },
    ),
    MilestoneKey.PER_MONTH to StatMeta(
      labelProvider = { _ -> DashboardString.MileStone.LbsPerMonth },
    ),
    MilestoneKey.PER_YEAR to StatMeta(
      labelProvider = { _ -> DashboardString.MileStone.LbsPerYear },
    ),
    MilestoneKey.TOTAL_CHANGE to StatMeta(
      labelProvider = { _ -> DashboardString.MileStone.LbsTotal },
    ),
    MilestoneKey.TO_GOAL to StatMeta(
      labelProvider = { _ -> DashboardString.MileStone.LbsToGoal },
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
    val valueSuffix: (Boolean) -> String = { _ -> "" },
    val icon: Int? = null,
  )
}
