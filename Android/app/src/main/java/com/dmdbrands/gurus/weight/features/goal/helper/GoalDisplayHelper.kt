package com.dmdbrands.gurus.weight.features.goal.helper

import com.dmdbrands.gurus.weight.domain.enums.GoalType

/**
 * Data class representing weightless settings, mirroring Angular Weightless interface.
 */
data class Weightless(
  val isWeightlessOn: Boolean,
  val weightlessWeight: Float,
)

/**
 * Helper class for goal display utilities.
 * Contains utility functions for goal calculations, mirroring Angular logic.
 */
object GoalDisplayHelper {
  /**
   * Transforms weight value similar to Angular weightPipe.transform().
   * Handles weightless logic and unit conversions.
   *
   * @param value Weight value to transform
   * @param showSymbol Whether to show + symbol for positive values
   * @param weightless Weightless settings (null if weightless mode is off)
   * @param isMetric Whether to use metric units
   * @return Transformed weight string with proper formatting
   */
  fun transformWeight(
    value: Double,
    showSymbol: Boolean = false,
    weightless: Weightless? = null,
  ): String {
    return if (showSymbol || weightless?.isWeightlessOn == true) {
      "${if (value > 0) "+" else ""}${String.format("%.1f", value)}"
    } else {
      String.format("%.1f", value)
    }
  }

  /**
   * Computes the distance to goal for display, following the Angular computeToGoal pattern exactly.
   * Mirrors the Angular logic with proper weightless support.
   *
   * @param goalWeight Goal weight value in stored format
   * @param latestEntryWeight Latest entry weight value in stored format (can be null or 0.0)
   * @param initialWeight Initial/starting weight value in stored format
   * @param weightless Weightless settings (null if weightless mode is off)
   * @return Distance to goal as formatted string
   */
  fun computeToGoal(
    goalWeight: Double,
    latestEntryWeight: Double?,
    initialWeight: Double,
    weightless: Weightless? = null,
  ): String {
    // If latest entry is null or 0.0, use initialWeight - goalWeight
    val toGoal = if (latestEntryWeight == null || latestEntryWeight == 0.0) {
      goalWeight - initialWeight
    } else {
      goalWeight - latestEntryWeight
    }
    // Return toGoal formatted to 1 decimal place
    return transformWeight(
      value = toGoal,
      showSymbol = true,
      weightless = weightless,
    )
  }

  /**
   * Computes display progress percentage based on goal type, following Angular stats-modal logic.
   * Maintain goals always show 100% progress, other goals are capped at 100%.
   */
  fun computeDisplayProgressPercentage(
    goalType: GoalType,
    goalPercent: Double?,
  ): Double {
    return if (goalType == GoalType.MAINTAIN) {
      100.0 // Maintain goals always show 100% progress
    } else {
      goalPercent?.let { kotlin.math.min(100.0, it) } ?: 0.0 // Cap other goals at 100%
    }
  }
}
