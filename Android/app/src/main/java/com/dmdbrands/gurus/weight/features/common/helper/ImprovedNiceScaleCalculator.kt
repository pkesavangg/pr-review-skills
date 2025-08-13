package com.greatergoods.meapp.features.common.helper

import kotlin.math.pow

object ImprovedNiceScaleCalculator {
  // Improved algorithm for Y-axis tick calculation optimized for gradual weight changes

  private val niceNumbers = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 25.0, 40.0, 50.0, 100.0)

  data class GraphMeta(
    val min: Double,
    val max: Double,
    val step: Double,
    val ticks: List<Double>,
    val domain: ClosedFloatingPointRange<Double>
  )

  fun generateNiceScale(
    minValue: Double,
    maxValue: Double,
    goalWeight: Double,
    targetTickCount: Int = 6
  ): GraphMeta {
    val actualMin = kotlin.math.floor(minValue)
    val actualMax = kotlin.math.ceil(maxValue)
    val rawRange = actualMax - actualMin

    return when {
      rawRange < 5.0 -> handleSmallRange(actualMin, actualMax, goalWeight)
      rawRange < 15.0 -> handleMediumRange(actualMin, actualMax, goalWeight)
      else -> handleNormalRange(actualMin, actualMax, goalWeight, targetTickCount)
    }
  }

  private fun handleSmallRange(dataMin: Double, dataMax: Double, goalWeight: Double): GraphMeta {
    val range = maxOf(dataMax - dataMin, 2.0)
    val padding = range * 0.2
    var min = kotlin.math.floor(dataMin - padding)
    var max = kotlin.math.ceil(dataMax + padding)

    val dataCenter = (dataMin + dataMax) / 2
    val dataRange = dataMax - dataMin
    val reasonableGoalRange = dataRange * 2

    val finalMin = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      minOf(min, kotlin.math.floor(goalWeight))
    else min

    val finalMax = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      maxOf(max, kotlin.math.ceil(goalWeight))
    else max

    val actualRange = finalMax - finalMin
    val step: Double
    val ticks = mutableListOf<Double>()

    if (actualRange == 2.0) {
      val evenMin = if (finalMin % 2 == 0.0) finalMin else finalMin - 1
      val evenMax = if (finalMax % 2 == 0.0) finalMax else finalMax + 1
      step = 2.0
      var current = evenMin
      while (current <= evenMax) {
        ticks.add(current)
        current += step
      }
      return GraphMeta(maxOf(evenMin, 0.0), evenMax, step, ticks.distinct().sorted(), evenMin..evenMax)
    } else {
      step = 1.0
      var current = finalMin
      while (current <= finalMax) {
        ticks.add(current)
        current += step
      }
      return GraphMeta(maxOf(finalMin, 0.0), finalMax, step, ticks.distinct().sorted(), finalMin..finalMax)
    }
  }

  private fun handleMediumRange(dataMin: Double, dataMax: Double, goalWeight: Double): GraphMeta {
    val range = dataMax - dataMin
    val padding = range * 0.15
    val min = kotlin.math.floor(dataMin - padding)
    val max = kotlin.math.ceil(dataMax + padding)

    val dataCenter = (dataMin + dataMax) / 2
    val reasonableGoalRange = range * 2

    val finalMin = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      listOf(min, kotlin.math.floor(goalWeight / 2) * 2, kotlin.math.floor(dataMin)).minOrNull()!!
    else min

    val finalMax = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      listOf(max, kotlin.math.ceil(goalWeight / 2) * 2, kotlin.math.ceil(dataMax)).maxOrNull()!!
    else max

    val step = 2.0
    val ticks = mutableListOf<Double>()
    var current = finalMin
    while (current <= finalMax) {
      ticks.add(current)
      current += step
    }

    return GraphMeta(maxOf(finalMin, 0.0), finalMax, step, ticks.distinct().sorted(), finalMin..finalMax)
  }

  private fun handleNormalRange(
    dataMin: Double,
    dataMax: Double,
    goalWeight: Double,
    targetTickCount: Int
  ): GraphMeta {
    val rawRange = dataMax - dataMin
    val minimumRange = maxOf(rawRange, 10.0)
    val padding = minimumRange * 0.1
    val paddedMin = dataMin - padding
    val paddedMax = dataMax + padding

    val step = calculateOptimalStep(paddedMax - paddedMin, targetTickCount)
    val niceMin = kotlin.math.floor(paddedMin / step) * step
    val niceMax = kotlin.math.ceil(paddedMax / step) * step

    val dataCenter = (dataMin + dataMax) / 2
    val reasonableGoalRange = rawRange * 2

    val finalMin = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      listOf(niceMin, kotlin.math.floor(goalWeight / step) * step, kotlin.math.floor(dataMin)).minOrNull()!!
    else niceMin

    val finalMax = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      listOf(niceMax, kotlin.math.ceil(goalWeight / step) * step, kotlin.math.ceil(dataMax)).maxOrNull()!!
    else niceMax

    var currentTick = finalMin
    var ticks = mutableListOf<Double>()
    while (currentTick <= finalMax + 0.001) {
      ticks.add(currentTick)
      currentTick += step
    }

    if (ticks.size < 3) {
      ticks.clear()
      val smallerStep = step / 2
      currentTick = finalMin
      while (currentTick <= finalMax + 0.001) {
        ticks.add(currentTick)
        currentTick += smallerStep
      }
    } else if (ticks.size > 6) {
      ticks.clear()
      val largerStep = step * 2
      currentTick = finalMin
      while (currentTick <= finalMax + 0.001) {
        ticks.add(currentTick)
        currentTick += largerStep
      }
    }

    return GraphMeta(maxOf(finalMin, 0.0), finalMax, step, ticks.distinct().sorted(), finalMin..finalMax)
  }

  private fun calculateOptimalStep(range: Double, targetTickCount: Int): Double {
    val roughStep = range / (targetTickCount - 1).coerceAtLeast(1)
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(roughStep)))
    val normalizedStep = roughStep / magnitude
    val closestNice = niceNumbers.minByOrNull { kotlin.math.abs(it - normalizedStep) } ?: 1.0
    return closestNice * magnitude
  }
}
