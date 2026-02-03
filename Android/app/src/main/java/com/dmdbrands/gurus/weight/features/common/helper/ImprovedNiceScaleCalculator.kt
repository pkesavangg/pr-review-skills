package com.greatergoods.meapp.features.common.helper

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/**
 * Data class representing Y-axis metadata including ticks.
 */
data class AxisMeta(
  val min: Double,
  val max: Double,
  val step: Double,
  val count: Int,
  val ticks: List<Double> = emptyList()
)

/**
 * Result data class for niceTicks method.
 */
internal data class NiceTicksResult(
  val ticks: List<Double>,
  val step: Double,
  val domainMin: Double,
  val domainMax: Double
)

/**
 * Result data class for enforceTickLimits method.
 */
internal data class TickLimitsResult(
  val step: Double,
  val ticks: List<Double>
)

/**
 * Result data class for applyEdgeBufferToTicks method.
 */
internal data class EdgeBufferResult(
  val step: Double,
  val ticks: List<Double>
)

/**
 * Improved algorithm for Y-axis tick calculation optimized for gradual weight changes.
 * Matches iOS implementation exactly.
 */
object ImprovedNiceScaleCalculator {
  // Expanded set of nice numbers used for step selection (normalized domain)
  private val niceNumbers = listOf(1.0, 2.0, 4.0, 5.0, 10.0, 15.0, 20.0, 25.0, 40.0, 50.0, 100.0, 200.0)

  // Classic nice numbers for niceTicks (1-2-5 × 10^n)
  private val classicNiceNumbers = listOf(1.0, 2.0, 5.0, 10.0)

  // Double equality epsilon
  private const val DOUBLE_EQUALITY_EPSILON = 1e-9

  /** Minimal padding above and below when min and max are the same (single data point). */
  private const val MIN_PADDING_SAME_VALUE = 1.0

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
    val result = niceTicks(min = effectiveMin, max = effectiveMax, desiredTickCount = desired)

    // Apply edge buffer so top/bottom data points don't touch chart edges
    val buffered = applyEdgeBufferToTicks(
      dataMin = dataMin,
      dataMax = dataMax,
      step = result.step,
      ticks = result.ticks,
      desiredTickCount = desired,
    )

    // Align domain to adjusted tick range so plot bounds match horizontal rules
    val domainMin = buffered.ticks.firstOrNull() ?: result.domainMin
    val domainMax = buffered.ticks.lastOrNull() ?: result.domainMax

    // Apply weightless mode logic: allow negative values if true, otherwise clamp to 0
    val finalMin = if (isWeightLessMode) domainMin else maxOf(domainMin, 0.0)
    val finalMax = domainMax

    // iOS sanitization step: if not in weightless mode and domainMin < 0, recalculate with max(3, buffered.ticks.size)
    val (finalTicks, finalStep) = if (!isWeightLessMode && domainMin < 0) {
      // Sanitize: recalculate with desiredTickCount = max(3, initial.ticks.count)
      val sanitizedDesiredTickCount = maxOf(3, buffered.ticks.size)
      val sanitizedResult = niceTicks(min = 0.0, max = finalMax, desiredTickCount = sanitizedDesiredTickCount)
      // Apply edge buffer again after sanitization
      val sanitizedBuffered = applyEdgeBufferToTicks(
        dataMin = dataMin,
        dataMax = dataMax,
        step = sanitizedResult.step,
        ticks = sanitizedResult.ticks,
        desiredTickCount = sanitizedDesiredTickCount,
      )
      Pair(sanitizedBuffered.ticks, sanitizedBuffered.step)
    } else {
      Pair(buffered.ticks, buffered.step)
    }

    return AxisMeta(
      min = finalMin,
      max = finalMax,
      step = finalStep,
      count = finalTicks.size,
      ticks = finalTicks,
    )
  }

  /**
   * Compute evenly spaced, human-friendly ticks using classic 1–2–5 × 10^n steps.
   * Returns ticks, step, and the snapped domain [niceMin, niceMax].
   * Matches iOS YAxisCalculator.niceTicks implementation.
   */
  private fun niceTicks(
    min: Double,
    max: Double,
    desiredTickCount: Int
  ): NiceTicksResult {
    val range = max - min
    if (!range.isFinite() || range <= 0 || desiredTickCount <= 1) {
      val lo = minOf(min, max)
      val hi = maxOf(min, max)
      val step = maxOf(hi - lo, 1.0)
      return NiceTicksResult(
        ticks = listOf(lo, hi),
        step = step,
        domainMin = lo,
        domainMax = hi,
      )
    }

    val rawInterval = range / (desiredTickCount - 1).coerceAtLeast(1)
    // Nearest power of 10
    val magnitude = 10.0.pow(floor(log10(kotlin.math.max(rawInterval, DOUBLE_EQUALITY_EPSILON))))
    val residual = rawInterval / magnitude

    val niceResidual: Double = when {
      residual.compareTo(1.0) <= 0 -> 1.0
      residual.compareTo(2.0) <= 0 -> 2.0
      residual.compareTo(5.0) <= 0 -> 5.0
      else -> 10.0
    }

    val step = niceResidual * magnitude

    // Snap bounds to multiples of step
    val niceMin = floor(min / step) * step
    val niceMax = ceil(max / step) * step

    // Build ticks
    val ticks = mutableListOf<Double>()
    var tick = niceMin
    // Small epsilon to ensure inclusion of upper bound
    val maxBound = niceMax + 1e-9
    while (tick.compareTo(maxBound) <= 0) {
      // Round to avoid floating artifacts at 10^n boundaries
      val rounded = round(tick / step) * step
      ticks.add(rounded)
      tick += step
    }

    // De-dup and sort for safety
    val deduped = ticks.distinct().sorted()
    val domainMin = deduped.firstOrNull() ?: niceMin
    val domainMax = deduped.lastOrNull() ?: niceMax
    val actualStep = if (deduped.size > 1) (deduped[1] - deduped[0]) else step

    return NiceTicksResult(
      ticks = deduped,
      step = actualStep,
      domainMin = domainMin,
      domainMax = domainMax,
    )
  }

  /**
   * Enforce tick limits (min 3, max 6) by adjusting step size.
   * Matches iOS YAxisCalculator.enforceTickLimits implementation.
   */
  internal fun enforceTickLimits(
    min: Double,
    max: Double,
    initialStep: Double
  ): TickLimitsResult {
    // Helper to snap values to the nearest nice multiple of step
    fun snapDown(value: Double, step: Double): Double {
      return if (step > 0) floor(value / step) * step else value
    }

    fun snapUp(value: Double, step: Double): Double {
      return if (step > 0) ceil(value / step) * step else value
    }

    fun buildTicks(min: Double, max: Double, step: Double): List<Double> {
      if (step <= 0) return listOf(min, max)
      val ticks = mutableListOf<Double>()
      var start = snapDown(min, step)
      val end = snapUp(max, step)
      // Guard against degenerate ranges
      if (end < start) return listOf(min, max)

      while (start <= end + 0.0001) {
        // Round to avoid floating artifacts
        val rounded = round(start / step) * step
        ticks.add(rounded)
        start += step
      }
      return ticks.distinct().sorted()
    }

    var step = maxOf(initialStep, 0.0001)
    val snappedMin = floor(min / step) * step
    val snappedMax = ceil(max / step) * step
    var ticks = buildTicks(min = snappedMin, max = snappedMax, step = step)

    // Adjust if too many ticks (> 6)
    while (ticks.size > 6) {
      step = pickNiceStep(threshold = step * 1.999)
      val sMin = floor(min / step) * step
      val sMax = ceil(max / step) * step
      ticks = buildTicks(min = sMin, max = sMax, step = step)
    }

    // Adjust if too few ticks (< 3)
    while (ticks.size < 3 && step > 0.1) {
      step = pickNiceStepAtMost(step / 2.001)
      val sMin = floor(min / step) * step
      val sMax = ceil(max / step) * step
      ticks = buildTicks(min = sMin, max = sMax, step = step)
    }

    // Final guard: ensure uniform spacing (all diffs equal within epsilon)
    if (ticks.size >= 3) {
      val diffs = ticks.zipWithNext { a, b -> b - a }
      val mean = diffs.average()
      val nonUniform = diffs.any { abs(it - mean) > maxOf(0.001, step * 0.05) }
      if (nonUniform) {
        // Force rebuild using the computed mean as step, snapped to a nice step
        val rng = (ticks.lastOrNull() ?: snappedMax) - (ticks.firstOrNull() ?: snappedMin)
        val snappedStep = calculateOptimalStep(
          range = rng,
          targetTickCount = maxOf(3, minOf(6, ticks.size)),
        )
        step = maxOf(snappedStep, 0.0001)
        val sMin = floor(min / step) * step
        val sMax = ceil(max / step) * step
        ticks = buildTicks(min = sMin, max = sMax, step = step)
      }
    }

    return TickLimitsResult(step, ticks)
  }

  /**
   * Ensures there is visual headroom/footroom between data extremes and the outermost ticks.
   * Keeps domain aligned with ticks to avoid gridline overflow beyond the plot area.
   * Matches iOS YAxisCalculator.applyEdgeBufferToTicks implementation.
   */
  internal fun applyEdgeBufferToTicks(
    dataMin: Double,
    dataMax: Double,
    step: Double,
    ticks: List<Double>,
    thresholdRatio: Double = 0.35,
    maxTicks: Int = 6,
    desiredTickCount: Int? = null
  ): EdgeBufferResult {
    if (ticks.isEmpty()) return EdgeBufferResult(step, ticks)

    var proposedMin = ticks.first()
    var proposedMax = ticks.last()
    var proposedStep = step

    // Determine if data is too close to outer ticks
    // Use <= to handle exact threshold matches conservatively (extend when distance equals threshold)
    val tooCloseToTop = (proposedMax - dataMax) <= (proposedStep * thresholdRatio)
    val tooCloseToBottom = (dataMin - proposedMin) <= (proposedStep * thresholdRatio)

    if (tooCloseToTop) proposedMax += proposedStep
    if (tooCloseToBottom) proposedMin -= proposedStep

    // Re-enforce tick limits and regenerate ticks uniformly using the original step
    // The step was already chosen optimally for the data range, so we preserve it when extending
    var enforced = enforceTickLimits(min = proposedMin, max = proposedMax, initialStep = proposedStep)

    // If still too close and we haven't exceeded soft cap, try to extend once more on each side
    if (enforced.ticks.isNotEmpty()) {
      val last = enforced.ticks.last()
      if ((last - dataMax) < (enforced.step * thresholdRatio) && enforced.ticks.size < maxTicks) {
        proposedMax = last + enforced.step
        enforced = enforceTickLimits(
          min = enforced.ticks.firstOrNull() ?: proposedMin,
          max = proposedMax,
          initialStep = enforced.step,
        )
      }

      val first = enforced.ticks.first()
      if ((dataMin - first) < (enforced.step * thresholdRatio) && enforced.ticks.size < maxTicks) {
        proposedMin = first - enforced.step
        enforced = enforceTickLimits(
          min = proposedMin,
          max = enforced.ticks.lastOrNull() ?: proposedMax,
          initialStep = enforced.step,
        )
      }
    }

    return EdgeBufferResult(enforced.step, enforced.ticks)
  }

  /**
   * Calculate optimal step size using nice numbers.
   * Matches iOS ImprovedNiceScaleCalculator.calculateOptimalStep implementation.
   */
  internal fun calculateOptimalStep(range: Double, targetTickCount: Int): Double {
    // Guard
    if (targetTickCount <= 1) return range
    val rough = kotlin.math.max(range / (targetTickCount - 1).coerceAtLeast(1), 0.0001)
    // magnitude 10^floor(log10(rough))
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    // Pick first nice >= normalized using expanded set
    val nice = niceNumbers.firstOrNull { it >= normalized } ?: niceNumbers.lastOrNull() ?: 1.0
    return nice * magnitude
  }

  /**
   * Pick a "nice" step size that is >= threshold using the nice numbers logic.
   * Matches iOS YAxisCalculator.pickNiceStep(atLeast:) implementation.
   */
  private fun pickNiceStep(threshold: Double): Double {
    // Determine magnitude (power of 10)
    val magnitude = 10.0.pow(floor(log10(kotlin.math.max(threshold, DOUBLE_EQUALITY_EPSILON))))
    val normalized = threshold / magnitude
    // Use expanded nice numbers
    val nice = niceNumbers.firstOrNull { it >= normalized } ?: niceNumbers.lastOrNull() ?: 1.0
    return nice * magnitude
  }

  /**
   * Pick the largest nice step <= threshold using expanded nice numbers.
   * Matches iOS YAxisCalculator.pickNiceStepAtMost implementation.
   */
  private fun pickNiceStepAtMost(threshold: Double): Double {
    val t = kotlin.math.max(threshold, DOUBLE_EQUALITY_EPSILON)
    val magnitude = 10.0.pow(floor(log10(t)))
    val normalized = t / magnitude
    val reversedNice = niceNumbers.sortedDescending()
    val candidate = reversedNice.firstOrNull { it <= normalized }
    return if (candidate != null) {
      candidate * magnitude
    } else {
      // Go down one order of magnitude using the largest nice number
      val largestNice = niceNumbers.lastOrNull() ?: 1.0
      largestNice * magnitude / largestNice
    }
  }
}
