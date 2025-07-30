package com.dmdbrands.gurus.weight.features.goal.helper

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import kotlin.math.abs
import android.util.Log

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
        isMetric: Boolean = false,
    ): String {
        val weight =
            if (weightless?.isWeightlessOn == true) {
                ConversionTools.convertStoredToDisplay(value - weightless.weightlessWeight, isMetric)
            } else {
                ConversionTools.convertStoredToDisplay(value, isMetric)
            }

        return if (showSymbol || weightless?.isWeightlessOn == true) {
            "${if (weight > 0) "+" else ""}${String.format("%.1f", weight)}"
        } else {
            String.format("%.1f", weight)
        }
    }

    /**
     * Computes the distance to goal for display, following the Angular computeToGoal pattern exactly.
     * Mirrors the Angular logic with proper weightless support.
     *
     * @param goalWeight Goal weight value in stored format
     * @param latestEntryWeight Latest entry weight value in stored format
     * @param goalType Goal type (maintain, lose, gain, etc.)
     * @param weightless Weightless settings (null if weightless mode is off)
     * @param isMetric Whether to use metric units
     * @return Distance to goal as number (not formatted string)
     */
    fun computeToGoal(
        goalWeight: Double,
        latestEntryWeight: Double,
        goalType: GoalType,
        weightless: Weightless? = null,
        isMetric: Boolean = false,
    ): Double {
        // Transform weights using weightPipe logic
        val transformedGoalWeight =
            transformWeight(
                value = goalWeight,
                showSymbol = false,
                weightless = weightless,
                isMetric = isMetric,
            ).toDoubleOrNull() ?: 0.0

        val transformedLatestWeight =
            transformWeight(
                value = latestEntryWeight,
                showSymbol = false,
                weightless = weightless,
                isMetric = isMetric,
            ).toDoubleOrNull() ?: 0.0

        // Calculate toGoal exactly like Angular
        val toGoal = transformedGoalWeight - transformedLatestWeight

        return when (goalType) {
            GoalType.MAINTAIN -> {
                if (toGoal == 0.0) {
                    return 0.0
                }
                val weightAwayFromGoalWeight = 0 - toGoal
                // Format to 1 decimal place and convert back to number like Angular
                String.format("%.1f", weightAwayFromGoalWeight).toDouble()
            }

            else -> {
                // For lose/gain goals, return toGoal formatted to 1 decimal place
                String.format("%.1f", toGoal).toDouble()
            }
        }
    }

    /**
     * Gets the formatted display text for distance to goal, matching Angular UI patterns.
     * This method handles the display formatting with proper + signs for maintain goals.
     *
     * @param goalWeight Goal weight value in stored format
     * @param latestEntryWeight Latest entry weight value in stored format
     * @param goalType Goal type (maintain, lose, gain, etc.)
     * @param weightless Weightless settings (null if weightless mode is off)
     * @param isMetric Whether to use metric units
     * @return Formatted string for display (e.g., "+1.5", "-2.0", "3.2")
     */
    fun getDistanceToGoalDisplayText(
        goalWeight: Double,
        latestEntryWeight: Double,
        goalType: GoalType,
        weightless: Weightless? = null,
        isMetric: Boolean = false,
    ): String {
        val distance = computeToGoal(goalWeight, latestEntryWeight, goalType, weightless, isMetric)

        return when (goalType) {
            GoalType.MAINTAIN -> {
                if (distance == 0.0) {
                    "0"
                } else if (distance > 0.0) {
                    "+${String.format("%.1f", distance).removeSuffix(".0")}"
                } else {
                    String.format("%.1f", distance).removeSuffix(".0")
                }
            }

            else -> {
                // For lose/gain goals, show absolute value without sign
                String.format("%.1f", abs(distance)).removeSuffix(".0")
            }
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use computeToGoal() with weightless support instead
     */
    @Deprecated("Use computeToGoal() with weightless support")
    fun computeGoal(
        currentWeight: Double,
        goalWeight: Double,
        goalType: GoalType,
    ): String {
        val distance =
            computeToGoal(
                goalWeight = goalWeight,
                latestEntryWeight = currentWeight,
                goalType = goalType,
                weightless = null,
                isMetric = false,
            )
        return getDistanceToGoalDisplayText(
            goalWeight = goalWeight,
            latestEntryWeight = currentWeight,
            goalType = goalType,
            weightless = null,
            isMetric = false,
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
        Log.d("goalpercent", goalPercent.toString())
        return if (goalType == GoalType.MAINTAIN) {
            100.0 // Maintain goals always show 100% progress
        } else {
            goalPercent?.let { kotlin.math.min(100.0, it) } ?: 0.0 // Cap other goals at 100%
        }
    }
}
