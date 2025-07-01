package com.greatergoods.meapp.features.goal.helper

import com.greatergoods.meapp.features.signup.model.GoalType
import kotlin.math.abs
import kotlin.math.min

/**
 * Helper class for goal display utilities.
 * Contains utility functions for goal calculations.
 */
object GoalDisplayHelper {

    /**
     * Computes the distance to goal for display, following the Angular computeToGoal pattern.
     * For maintain goals, returns formatted string with +/- indicating distance from goal weight.
     * For lose/gain goals, returns the absolute distance to goal.
     *
     * @param currentWeight Current weight value
     * @param goalWeight Goal weight value
     * @param goalType Goal type ("maintain", "lose", "gain", etc.)
     * @return Formatted string representing distance to goal
     */
    fun computeGoal(currentWeight: Double, goalWeight: Double, goalType: GoalType): String {
        val toGoal = goalWeight - currentWeight

        return when (goalType) {
            GoalType.MAINTAIN, -> {
                if (toGoal == 0.0) {
                    return "0.0"
                }
                val weightAwayFromGoalWeight = 0 - toGoal
                when {
                    weightAwayFromGoalWeight > 0 -> "+${String.format("%.1f", abs(weightAwayFromGoalWeight))}"
                    else -> String.format("%.1f", weightAwayFromGoalWeight)
                }
            }
            else -> {
                // For lose/gain goals, return absolute distance
                String.format("%.1f", abs(toGoal))
            }
        }
    }

    /**
     * Computes display progress percentage based on goal type, following Angular stats-modal logic.
     * Maintain goals always show 100% progress, other goals are capped at 100%.
     */
    fun computeDisplayProgressPercentage(goalType: GoalType, goalPercent: Double?): Double {
        return if (goalType == GoalType.MAINTAIN) {
            100.0 // Maintain goals always show 100% progress
        } else {
            goalPercent?.let { min(100.0, it) } ?: 0.0 // Cap other goals at 100%
        }
    }
}
