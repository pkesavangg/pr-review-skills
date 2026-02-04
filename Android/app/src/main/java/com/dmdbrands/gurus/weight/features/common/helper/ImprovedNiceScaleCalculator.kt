package com.greatergoods.meapp.features.common.helper

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Data class representing Y-axis metadata (min, max, step, tick count).
 */
data class AxisMeta(
  val min: Double,
  val max: Double,
  val step: Double,
  val count: Int
)

/**
 * Improved algorithm for Y-axis tick calculation optimized for gradual weight changes.
 * Matches iOS implementation exactly.
 */
object ImprovedNiceScaleCalculator {
  /** Classic nice numbers (1–2–5 × 10ⁿ) used by step pickers to avoid steps like 0.4, 4, 15, 25. */
  private val classicNiceNumbers = listOf(1.0, 2.0, 5.0, 10.0)

  private const val DOUBLE_EQUALITY_EPSILON = 1e-9
  private const val MIN_PADDING_SAME_VALUE = 1.0
  private const val TICK_BOUND_EPSILON = 1e-9

  /** Snaps value down to the nearest multiple of step. */
  private fun snapDown(value: Double, step: Double): Double =
    if (step > 0) floor(value / step) * step else value

  /** Snaps value up to the nearest multiple of step. */
  private fun snapUp(value: Double, step: Double): Double =
    if (step > 0) ceil(value / step) * step else value

  /**
   * Builds a sorted list of ticks from min to max using the given step.
   * Uses shared snap logic to avoid floating-point artifacts.
   */
  private fun buildTicksFromStep(min: Double, max: Double, step: Double): List<Double> {
    if (step <= 0) return listOf(min, max)
    val start = snapDown(min, step)
    val end = snapUp(max, step)
    if (end < start) return listOf(min, max)
    val ticks = mutableListOf<Double>()
    var tick = start
    val maxBound = end + TICK_BOUND_EPSILON
    while (tick <= maxBound) {
      ticks.add(round(tick / step) * step)
      tick += step
    }
    return ticks.distinct().sorted()
  }

  /**
   * Returns the smallest classic nice number (1, 2, 5, 10) × 10^n that is >= value.
   * Used for step sizing in niceTicks and enforceTickLimits.
   */
  private fun nextNice(value: Double): Double {
    val safe = kotlin.math.max(value, DOUBLE_EQUALITY_EPSILON)
    val magnitude = 10.0.pow(floor(log10(safe)))
    val normalized = value / magnitude
    val nice = classicNiceNumbers.firstOrNull { it >= normalized } ?: 10.0
    return nice * magnitude
  }

  /**
   * Returns the largest classic nice number (1, 2, 5, 10) × 10^n that is <= value.
   * Used when reducing step count in enforceTickLimits.
   */
  private fun prevNice(value: Double): Double {
    val safe = kotlin.math.max(value, DOUBLE_EQUALITY_EPSILON)
    val magnitude = 10.0.pow(floor(log10(safe)))
    val normalized = value / magnitude
    val candidate = classicNiceNumbers.sortedDescending().firstOrNull { it <= normalized }
    return if (candidate != null) {
      candidate * magnitude
    } else {
      magnitude / 10.0
    }
  }

  /**
   * Generate a nice Y-axis scale with optimal tick values for gradual changes.
   * Matches iOS ImprovedNiceScaleCalculator.generateNiceScale implementation.
   */
  fun generateNiceScale(
    minValue: Double,
    maxValue: Double,
    goalWeight: Double,
    isWeightLessMode: Boolean = false,
    targetTickCount: Int = 6
  ): AxisMeta {
    val dataMin = minOf(minValue, maxValue)
    val dataMax = maxOf(minValue, maxValue)
    val desired = maxOf(3, minOf(6, targetTickCount))

    // When min and max are the same, use minimal padding above and below instead of zero range
    val range = dataMax - dataMin
    val isSameValue = !range.isFinite() || range <= 0 || abs(range) < DOUBLE_EQUALITY_EPSILON
    val (effectiveMin, effectiveMax) = if (isSameValue) {
      val center = (dataMin + dataMax) / 2.0
      Pair(center - MIN_PADDING_SAME_VALUE, center + MIN_PADDING_SAME_VALUE)
    } else {
      Pair(dataMin, dataMax)
    }

    // Generate nice ticks using classic algorithm (iOS matches this)
    val (resultTicks, resultStep, resultDomain) = niceTicks(min = effectiveMin, max = effectiveMax, desiredTickCount = desired)

    // Apply edge buffer so top/bottom data points don't touch chart edges
    val (bufferedStep, bufferedTicks) = applyEdgeBufferToTicks(
      dataMin = dataMin,
      dataMax = dataMax,
      step = resultStep,
      ticks = resultTicks,
      desiredTickCount = desired,
    )

    // Align domain to adjusted tick range so plot bounds match horizontal rules
    val domainMin = bufferedTicks.firstOrNull() ?: resultDomain.first
    val domainMax = bufferedTicks.lastOrNull() ?: resultDomain.second

    // Apply weightless mode logic: allow negative values if true, otherwise clamp to 0
    val finalMin = if (isWeightLessMode) domainMin else maxOf(domainMin, 0.0)
    val finalMax = domainMax

    // iOS sanitization step: if not in weightless mode and domainMin < 0, recalculate with max(3, buffered.ticks.size)
    val (finalTicks, finalStep) = if (!isWeightLessMode && domainMin < 0) {
      val sanitizedDesiredTickCount = maxOf(3, bufferedTicks.size)
      val (sanitizedTicks, sanitizedStep, _) = niceTicks(min = 0.0, max = finalMax, desiredTickCount = sanitizedDesiredTickCount)
      val (sanitizedBufferedStep, sanitizedBufferedTicks) = applyEdgeBufferToTicks(
        dataMin = dataMin,
        dataMax = dataMax,
        step = sanitizedStep,
        ticks = sanitizedTicks,
        desiredTickCount = sanitizedDesiredTickCount,
      )
      Pair(sanitizedBufferedTicks, sanitizedBufferedStep)
    } else {
      Pair(bufferedTicks, bufferedStep)
    }

    return AxisMeta(
      min = finalMin,
      max = finalMax,
      step = finalStep,
      count = finalTicks.size
    )
  }

  /**
   * Compute evenly spaced, human-friendly ticks using classic 1–2–5 × 10^n steps.
   * Returns (ticks, step, domainMin to domainMax as Pair).
   * Matches iOS YAxisCalculator.niceTicks implementation.
   */
  private fun niceTicks(
    min: Double,
    max: Double,
    desiredTickCount: Int
  ): Triple<List<Double>, Double, Pair<Double, Double>> {
    val range = max - min
    if (!range.isFinite() || range <= 0 || desiredTickCount <= 1) {
      val lo = minOf(min, max)
      val hi = maxOf(min, max)
      val step = maxOf(hi - lo, 1.0)
      return Triple(listOf(lo, hi), step, Pair(lo, hi))
    }

    val rawInterval = range / (desiredTickCount - 1).coerceAtLeast(1)
    var step = nextNice(rawInterval)
    if (step < 1.0) step = 1.0

    val niceMin = snapDown(min, step)
    val niceMax = snapUp(max, step)
    val ticks = buildTicksFromStep(niceMin, niceMax, step)

    val domainMin = ticks.firstOrNull() ?: niceMin
    val domainMax = ticks.lastOrNull() ?: niceMax
    val actualStep = if (ticks.size > 1) ticks[1] - ticks[0] else step

    return Triple(ticks, actualStep, Pair(domainMin, domainMax))
  }

  /**
   * Enforce tick limits (min 3, max 6) by adjusting step size.
   * Returns Pair(step, ticks). Matches iOS YAxisCalculator.enforceTickLimits implementation.
   */
  internal fun enforceTickLimits(
    min: Double,
    max: Double,
    initialStep: Double
  ): Pair<Double, List<Double>> {
    var step = maxOf(initialStep, DOUBLE_EQUALITY_EPSILON)
    var snappedMin = snapDown(min, step)
    var snappedMax = snapUp(max, step)
    var ticks = buildTicksFromStep(snappedMin, snappedMax, step)

    while (ticks.size > 6) {
      step = nextNice(step * 1.999)
      snappedMin = snapDown(min, step)
      snappedMax = snapUp(max, step)
      ticks = buildTicksFromStep(snappedMin, snappedMax, step)
    }

    while (ticks.size < 3 && step > 0.1) {
      step = prevNice(step / 2.001)
      snappedMin = snapDown(min, step)
      snappedMax = snapUp(max, step)
      ticks = buildTicksFromStep(snappedMin, snappedMax, step)
    }

    if (ticks.size >= 3) {
      val diffs = ticks.zipWithNext { a, b -> b - a }
      val mean = diffs.average()
      val nonUniform = diffs.any { abs(it - mean) > maxOf(0.001, step * 0.05) }
      if (nonUniform) {
        val rng = (ticks.lastOrNull() ?: snappedMax) - (ticks.firstOrNull() ?: snappedMin)
        step = maxOf(
          calculateOptimalStep(rng, maxOf(3, minOf(6, ticks.size))),
          DOUBLE_EQUALITY_EPSILON
        )
        snappedMin = snapDown(min, step)
        snappedMax = snapUp(max, step)
        ticks = buildTicksFromStep(snappedMin, snappedMax, step)
      }
    }

    return Pair(step, ticks)
  }

  /**
   * Ensures there is visual headroom/footroom between data extremes and the outermost ticks.
   * Returns Pair(step, ticks). Matches iOS YAxisCalculator.applyEdgeBufferToTicks implementation.
   */
  internal fun applyEdgeBufferToTicks(
    dataMin: Double,
    dataMax: Double,
    step: Double,
    ticks: List<Double>,
    thresholdRatio: Double = 0.35,
    maxTicks: Int = 6,
    desiredTickCount: Int? = null
  ): Pair<Double, List<Double>> {
    if (ticks.isEmpty()) return Pair(step, ticks)

    var proposedMin = ticks.first()
    var proposedMax = ticks.last()
    var proposedStep = step

    val tooCloseToTop = (proposedMax - dataMax) <= (proposedStep * thresholdRatio)
    val tooCloseToBottom = (dataMin - proposedMin) <= (proposedStep * thresholdRatio)

    if (tooCloseToTop) proposedMax += proposedStep
    if (tooCloseToBottom) proposedMin -= proposedStep

    var (enforcedStep, enforcedTicks) = enforceTickLimits(min = proposedMin, max = proposedMax, initialStep = proposedStep)

    if (enforcedTicks.isNotEmpty()) {
      val last = enforcedTicks.last()
      if ((last - dataMax) < (enforcedStep * thresholdRatio) && enforcedTicks.size < maxTicks) {
        proposedMax = last + enforcedStep
        val next = enforceTickLimits(
          min = enforcedTicks.firstOrNull() ?: proposedMin,
          max = proposedMax,
          initialStep = enforcedStep,
        )
        enforcedStep = next.first
        enforcedTicks = next.second
      }

      val first = enforcedTicks.first()
      if ((dataMin - first) < (enforcedStep * thresholdRatio) && enforcedTicks.size < maxTicks) {
        proposedMin = first - enforcedStep
        val next = enforceTickLimits(
          min = proposedMin,
          max = enforcedTicks.lastOrNull() ?: proposedMax,
          initialStep = enforcedStep,
        )
        enforcedStep = next.first
        enforcedTicks = next.second
      }
    }

    return Pair(enforcedStep, enforcedTicks)
  }

  /**
   * Calculate optimal step size using classic nice numbers (1–2–5 × 10ⁿ) only.
   * Avoids steps like 0.4, 1.5, 4, 15, 25, 40.
   */
  internal fun calculateOptimalStep(range: Double, targetTickCount: Int): Double {
    if (targetTickCount <= 1) return range
    val rough = kotlin.math.max(range / (targetTickCount - 1).coerceAtLeast(1), DOUBLE_EQUALITY_EPSILON)
    return nextNice(rough)
  }
}
