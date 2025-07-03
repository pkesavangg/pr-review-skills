package com.greatergoods.meapp.features.common.helper

import com.greatergoods.meapp.core.shared.utilities.ConversionTools
import com.greatergoods.meapp.domain.model.api.goal.GoalData
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * Helper object for Account-related operations and conversions.
 */
object AccountHelper {
    /**
     * Extracts goal data from an Account object with proper weight conversions.
     * Converts stored weight values to the format expected by the API.
     *
     * @return GoalData object with converted weight values
     */
    fun Account.extractGoalData(): GoalData {
        val goalType = this.goalType ?: "maintain"
        val goalWeight = this.goalWeight ?: 0.0
        val initialWeight = this.initialWeight
        val metPreviousGoal = this.metPreviousGoal

        // Convert weights based on user's preferred unit
        val isMetric = this.weightUnit == WeightUnit.KG

        // Convert stored weights to display format for API consistency
        val convertedGoalWeight = ConversionTools.convertStoredToDisplay(goalWeight, isMetric)
        val convertedInitialWeight = ConversionTools.convertStoredToDisplay(initialWeight, isMetric)

        return GoalData(
            goalWeight = convertedGoalWeight,
            initialWeight = convertedInitialWeight,
            type = goalType,
            metPreviousGoal = metPreviousGoal,
        )
    }

    /**
     * Converts display weight to stored format based on account's weight unit preference.
     *
     * @param displayWeight Weight in display format (kg or lbs)
     * @return Weight in stored format
     */
    fun Account.convertDisplayWeightToStored(displayWeight: Double): Double {
        val isMetric = this.weightUnit == WeightUnit.KG
        return ConversionTools.convertDisplayToStored(displayWeight.toInt(), isMetric = isMetric)
    }

    /**
     * Converts stored weight to display format based on account's weight unit preference.
     *
     * @param storedWeight Weight in stored format
     * @return Weight in display format (kg or lbs)
     */
    fun Account.convertStoredWeightToDisplay(storedWeight: Double): Double {
        val isMetric = this.weightUnit == WeightUnit.KG
        return ConversionTools.convertStoredToDisplay(storedWeight, isMetric)
    }

    /**
     * Checks if the account uses metric units.
     *
     * @return true if metric (kg), false if imperial (lbs)
     */
    fun Account.isMetricUnit(): Boolean = this.weightUnit == WeightUnit.KG

    /**
     * Formats weight for display with appropriate unit.
     *
     * @param weight Weight in stored format
     * @return Formatted weight string (e.g., "70.5 kg" or "155.2 lbs")
     */
    fun Account.formatWeightForDisplay(weight: Double): String {
        val displayWeight = convertStoredWeightToDisplay(weight)
        val unit = this.weightUnit.label
        return String.format("%.1f %s", displayWeight, unit)
    }
}
