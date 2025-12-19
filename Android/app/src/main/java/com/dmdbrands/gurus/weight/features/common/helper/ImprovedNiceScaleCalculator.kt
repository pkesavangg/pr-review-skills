package com.greatergoods.meapp.features.common.helper

import kotlin.math.pow

data class AxisMeta(
  val min: Double,
  val max: Double,
  val step: Double,
  val count: Int,
)

object ImprovedNiceScaleCalculator {
  // Improved algorithm for Y-axis tick calculation optimized for gradual weight changes

  private val niceNumbers = listOf(1.0, 4.0, 5.0, 10.0, 15.0, 20.0, 25.0, 40.0, 50.0, 100.0)

  fun generateNiceScale(
    minValue: Double,
    maxValue: Double,
    goalWeight: Double,
    isWeightLessMode: Boolean = false,
    targetTickCount: Int = 5
  ): AxisMeta {
    val actualMin = kotlin.math.floor(minValue)
    val actualMax = kotlin.math.ceil(maxValue)
    val rawRange = actualMax - actualMin

    return when {
      rawRange < 5.0 -> handleSmallRange(actualMin, actualMax, goalWeight, isWeightLessMode, targetTickCount)
      rawRange < 15.0 -> handleMediumRange(actualMin, actualMax, goalWeight, isWeightLessMode, targetTickCount)
      else -> handleNormalRange(actualMin, actualMax, goalWeight, isWeightLessMode, targetTickCount)
    }
  }

  private fun handleSmallRange(
    dataMin: Double,
    dataMax: Double,
    goalWeight: Double,
    isWeightLessMode: Boolean,
    targetTickCount: Int
  ): AxisMeta {
    val range = maxOf(dataMax - dataMin, 2.0)
    val padding = range * 0.2
    val paddedMin = dataMin - padding
    val paddedMax = dataMax + padding

    val dataCenter = (dataMin + dataMax) / 2
    val dataRange = dataMax - dataMin
    val reasonableGoalRange = dataRange * 2

    // Include goal weight if reasonable
    val minBound = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      minOf(paddedMin, goalWeight)
    else paddedMin

    val maxBound = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      maxOf(paddedMax, goalWeight)
    else paddedMax

    // Calculate nice step based on range and target tick count
    val rawRange = maxBound - minBound
    val roughStep = rawRange / (targetTickCount - 1).coerceAtLeast(1)
    val step = pickNiceStep(roughStep)

    // Align min/max to nice step
    val niceMin = kotlin.math.floor(minBound / step) * step
    val niceMax = kotlin.math.ceil(maxBound / step) * step

    // Apply weightless mode logic: allow negative values if true, otherwise clamp to 0
    val finalMin = if (isWeightLessMode) niceMin else maxOf(niceMin, 0.0)
    val finalMax = niceMax

    // Generate ticks and ensure count doesn't exceed target tick count
    val ticks = generateTicks(finalMin, finalMax, step, targetTickCount)

    return AxisMeta(finalMin, finalMax, step, ticks.size)
  }

  private fun handleMediumRange(
    dataMin: Double,
    dataMax: Double,
    goalWeight: Double,
    isWeightLessMode: Boolean,
    targetTickCount: Int
  ): AxisMeta {
    val range = dataMax - dataMin
    val padding = range * 0.15
    val paddedMin = dataMin - padding
    val paddedMax = dataMax + padding

    val dataCenter = (dataMin + dataMax) / 2
    val reasonableGoalRange = range * 2

    // Include goal weight if reasonable
    val minBound = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      minOf(paddedMin, goalWeight)
    else paddedMin

    val maxBound = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      maxOf(paddedMax, goalWeight)
    else paddedMax

    // Calculate nice step based on range and target tick count
    val rawRange = maxBound - minBound
    val roughStep = rawRange / (targetTickCount - 1).coerceAtLeast(1)
    val step = pickNiceStep(roughStep)

    // Align min/max to nice step
    val niceMin = kotlin.math.floor(minBound / step) * step
    val niceMax = kotlin.math.ceil(maxBound / step) * step

    // Apply weightless mode logic: allow negative values if true, otherwise clamp to 0
    val finalMin = if (isWeightLessMode) niceMin else maxOf(niceMin, 0.0)
    val finalMax = niceMax

    // Generate ticks and ensure count doesn't exceed target tick count
    val ticks = generateTicks(finalMin, finalMax, step, targetTickCount)

    return AxisMeta(finalMin, finalMax, step, ticks.size)
  }

  private fun handleNormalRange(
    dataMin: Double,
    dataMax: Double,
    goalWeight: Double,
    isWeightLessMode: Boolean,
    targetTickCount: Int
  ): AxisMeta {
    val rawRange = dataMax - dataMin
    val minimumRange = maxOf(rawRange, 10.0)
    val padding = minimumRange * 0.1
    val paddedMin = dataMin - padding
    val paddedMax = dataMax + padding

    val dataCenter = (dataMin + dataMax) / 2
    val reasonableGoalRange = rawRange * 2

    // Include goal weight if reasonable
    val minBound = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      minOf(paddedMin, goalWeight)
    else paddedMin

    val maxBound = if (kotlin.math.abs(goalWeight - dataCenter) <= reasonableGoalRange)
      maxOf(paddedMax, goalWeight)
    else paddedMax

    // Calculate nice step based on range and target tick count
    val range = maxBound - minBound
    val roughStep = range / (targetTickCount - 1).coerceAtLeast(1)
    val step = pickNiceStep(roughStep)

    // Align min/max to nice step
    val niceMin = kotlin.math.floor(minBound / step) * step
    val niceMax = kotlin.math.ceil(maxBound / step) * step

    // Apply weightless mode logic: allow negative values if true, otherwise clamp to 0
    val finalMin = if (isWeightLessMode) niceMin else maxOf(niceMin, 0.0)
    val finalMax = niceMax

    // Generate ticks and ensure count doesn't exceed target tick count
    val ticks = generateTicks(finalMin, finalMax, step, targetTickCount)

    return AxisMeta(finalMin, finalMax, step, ticks.size)
  }

  /**
   * Generate ticks between min and max with the given step, ensuring count doesn't exceed target tick count.
   */
  private fun generateTicks(min: Double, max: Double, step: Double, targetTickCount: Int): List<Double> {
    val ticks = mutableListOf<Double>()
    var current = min

    while (current <= max && ticks.size < targetTickCount) {
      ticks.add(current)
      current += step
    }

    return ticks.distinct().sorted()
  }

  /**
   * Pick a "nice" step size that is >= threshold using the nice numbers logic.
   * This ensures step values are always from the predefined nice numbers list.
   */
  private fun pickNiceStep(threshold: Double): Double {
    // Determine magnitude (power of 10)
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(maxOf(threshold, 1e-9))))
    val normalized = threshold / magnitude

    // Find first nice number >= normalized, otherwise bump magnitude
    val candidate = niceNumbers.firstOrNull { it >= normalized }
    return if (candidate != null && candidate * magnitude > 1) {
      candidate * magnitude
    } else {
      // If none is big enough, move to next order of magnitude with the smallest nice number
      (niceNumbers.firstOrNull() ?: 1.0) * magnitude * 10.0
    }
  }
}
