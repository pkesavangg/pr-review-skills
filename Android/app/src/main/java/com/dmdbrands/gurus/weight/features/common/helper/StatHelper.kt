package com.dmdbrands.gurus.weight.features.common.helper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.DashboardMetric
import com.dmdbrands.gurus.weight.features.common.helper.StatMeta.metricStatMetaMap
import com.dmdbrands.gurus.weight.features.common.helper.StatMeta.milestoneStatMetaMap
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.common.strings.MetricLabels
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded
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

  fun getMetricValue(
    item: DashboardMetric,
    key: MetricKey,
    useShort: Boolean = false,
    showMetricIcon: Boolean = false
  ): Stat {
    val value = when (key) {
      MetricKey.WEIGHT -> item.weight?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.BMI -> item.bmi?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.BODY_FAT -> item.bodyFat?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.MUSCLE_MASS -> item.muscleMass?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.BODY_WATER -> item.bodyWater?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.HEART_RATE -> item.heartRate
      MetricKey.BONE_MASS -> item.boneMass?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.VISCERAL_FAT -> item.visceralFatLevel?.roundToInt()
      MetricKey.SUBCUTANEOUS_FAT -> item.subcutaneousFatPercent?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.PROTEIN -> item.proteinPercent?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.SKELETAL_MUSCLE -> item.skeletalMusclePercent?.toDouble()?.let { kotlin.math.round(it * 100) / 100 }
      MetricKey.BMR -> item.bmr?.toInt()
      MetricKey.METABOLIC_AGE -> item.metabolicAge?.toInt()
      else -> null
    }
    return DashboardKey.Metric(key).toStat(value, useShort, item.unit, showMetricIcon = showMetricIcon)
  }

  /**
   * Create a Stat for a given DashboardKey and value. Handles special cases.
   */
  fun DashboardKey.toStat(
    value: Any?,
    useShort: Boolean = false,
    unit: WeightUnit = WeightUnit.LB,
    showMetricIcon: Boolean = false
  ): Stat {
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
      is Number -> {
        if (value == 0.0 || value == 0) {
          null
        } else if (this is DashboardKey.Metric && this.key == MetricKey.WEIGHT) {
          // Format weight values to remove .0 if whole number
          formatWeightValue(value.toDouble())
        } else {
          value.toString()
        }
      }
      is String -> if (value.isBlank() || value == "0") null else value
      else -> null
    }
    val prefix = meta.valuePrefix(useShort).takeIf { it.isNotEmpty() }
    
    // Handle pluralization for streak milestones (day vs days)
    val suffix = if (this is DashboardKey.Milestone && 
        (this.key == MilestoneKey.CURRENT_STREAK || this.key == MilestoneKey.LONGEST_STREAK) &&
        value is Number) {
      val numValue = value.toDouble()
      if (numValue <= 1.0) "day" else "days"
    } else {
      meta.valueSuffix(useShort).takeIf { it.isNotEmpty() }
    }
    
    val calculatedUnit = if (this is DashboardKey.Metric && (this.key == MetricKey.WEIGHT)) {
      unit.label
    } else {
      meta.unitProvider(useShort)
    }
    val labelProviderCondition = if (this is DashboardKey.Milestone) unit == WeightUnit.LB else useShort
    return Stat(
      label = meta.labelProvider(labelProviderCondition),
      value = valueStr,
      unit = calculatedUnit,
      icon = if (showMetricIcon || this is DashboardKey.Milestone) meta.icon else null,
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
    showMetricIcon: Boolean = false,
    filterNulls: Boolean = true
  ): List<Stat> {
    // Pre-filter keys to avoid repeated filtering
    val keysToUse = (visibleKeys ?: MetricKey.entries).filter { key ->
      !filterWeight || key != MetricKey.WEIGHT
    }

    // Use buildList for better performance with large datasets
    return buildList {
      for (key in keysToUse) {
        val stat = if (item != null) {
          getMetricValue(item, key, useShort, showMetricIcon = showMetricIcon)
        } else {
          DashboardKey.Metric(key).toStat(null, useShort, showMetricIcon = showMetricIcon)
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
    showMetricIcon: Boolean = false,
    filterNulls: Boolean = true,
    unit: WeightUnit = WeightUnit.LB
  ): List<Stat> {
    val keysToUse = (visibleKeys ?: MilestoneKey.entries)

    val stats = keysToUse.map { key ->
      val value = getMilestoneValue(progress, key)
      DashboardKey.Milestone(key).toStat(value, useShort,unit, showMetricIcon = showMetricIcon)
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
      MilestoneKey.PER_WEEK -> progress.week.takeIf { it != null }?.let {
        if (it >= 0) "+${it.rounded()}" else it.rounded().toString()
      }

      MilestoneKey.PER_MONTH -> progress.month.takeIf { it != null }?.let {
        if (it >= 0) "+${it.rounded()}" else it.rounded().toString()
      }

      MilestoneKey.PER_YEAR -> progress.year.takeIf { it != null }?.let {
        if (it >= 0) "+${it.rounded()}" else it.rounded().toString()
      }

      MilestoneKey.TOTAL_CHANGE -> progress.total.takeIf { it != null }?.let {
        if (it >= 0) "+${it.rounded()}" else it.rounded().toString()
      }

      else -> null
    }
  }

  /**
   * Returns the additional 8 metrics (excluding the basic 4 metrics) in camelCase format.
   * These are the metrics that get added when upgrading from 4-metric to 12-metric dashboard.
   */
  fun getAdditionalMetrics(): List<String> = listOf(
    MetricKeyConstants.BONE_MASS,
    MetricKeyConstants.VISCERAL_FAT,
    MetricKeyConstants.SUBCUTANEOUS_FAT,
    MetricKeyConstants.PROTEIN,
    MetricKeyConstants.SKELETAL_MUSCLE,
    MetricKeyConstants.BMR,
    MetricKeyConstants.METABOLIC_AGE,
    MetricKeyConstants.HEART_RATE,
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
      unitProvider = { useShort -> if (useShort) "bpm" else null },
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
      labelProvider = { isLb -> if (isLb) DashboardString.MileStone.LbsPerWeek else DashboardString.MileStone.KgsPerWeek },
    ),
    MilestoneKey.PER_MONTH to StatMeta(
      labelProvider = { isLb -> if (isLb) DashboardString.MileStone.LbsPerMonth else DashboardString.MileStone.KgsPerMonth },
    ),
    MilestoneKey.PER_YEAR to StatMeta(
      labelProvider = { isLb -> if (isLb) DashboardString.MileStone.LbsPerYear else DashboardString.MileStone.KgsPerYear },
    ),
    MilestoneKey.TOTAL_CHANGE to StatMeta(
      labelProvider = { isLb -> if (isLb) DashboardString.MileStone.LbsTotal else DashboardString.MileStone.KgsTotal },
    ),
    MilestoneKey.TO_GOAL to StatMeta(
      labelProvider = { isLb -> if (isLb) DashboardString.MileStone.LbsToGoal else DashboardString.MileStone.KgsToGoal },
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
