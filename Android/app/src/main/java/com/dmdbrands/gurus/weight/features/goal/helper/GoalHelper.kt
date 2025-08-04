package com.dmdbrands.gurus.weight.features.goal.helper

import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.goal.model.GoalFormControls
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import kotlin.math.round

/**
 * Helper functions for Goal-related operations.
 */
object GoalHelper {
    /**
     * Safely converts a FormControl<String> to Double with a default value.
     */
    fun FormControl<String>.toDoubleSafe(default: Double = 0.0): Double = this.value.toDoubleOrNull() ?: default

    /**
     * Rounds a Double to one decimal place.
     */
    fun Double?.rounded(): Double? = this?.let { round(it * 10) / 10 }

    /**
     * Core function to create a Goal with proper weight conversion.
     * Used by both form submission and signup flows.
     */
    private fun createGoalInternal(
        currentWeight: Double,
        goalWeight: Double,
        goalType: String,
        fromUnit: WeightUnit,
        toUnit: WeightUnit
    ): Goal {
        // Convert and round weights
        val processedCurrentWeight = processWeight(currentWeight, fromUnit, toUnit)
        val processedGoalWeight = processWeight(goalWeight, fromUnit, toUnit)

        // Determine specific goal type
        val specificGoalType = if (goalType == GoalType.MAINTAIN.value) {
            "maintain"
        } else {
            if (processedGoalWeight <= processedCurrentWeight) "lose" else "gain"
        }

        return Goal(
            goalWeight = processedGoalWeight,
            initialWeight = processedCurrentWeight,
            type = specificGoalType
        )
    }

    /**
     * Converts weight between units.
     */
    fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
        return when {
            from == to -> value
            from == WeightUnit.KG && to == WeightUnit.LB -> value * 2.20462
            from == WeightUnit.LB && to == WeightUnit.KG -> value / 2.20462
            else -> value
        }
    }

    /**
     * Processes weight with unit conversion and rounding.
     */
    fun processWeight(weight: Double, fromUnit: WeightUnit, toUnit: WeightUnit): Double {
        val convertedWeight = convertWeight(weight, fromUnit, toUnit)
        return convertedWeight.rounded() ?: weight
    }

    /**
     * Creates a Goal model from form controls.
     * Uses the core createGoalInternal function.
     */
    fun GoalFormControls.toGoal(fromUnit: WeightUnit, toUnit: WeightUnit): Goal {
        return createGoalInternal(
            currentWeight = this.currentWeight.toDoubleSafe(),
            goalWeight = this.goalWeight.toDoubleSafe(),
            goalType = this.goalType.value,
            fromUnit = fromUnit,
            toUnit = toUnit
        )
    }

    /**
     * Creates a Goal model from signup data.
     * Uses the same core createGoalInternal function as form submission.
     */
    fun createGoal(
        currentWeight: Double,
        goalWeight: Double,
        goalType: String,
        fromUnit: WeightUnit,
        toUnit: WeightUnit
    ): Goal = createGoalInternal(
        currentWeight = currentWeight,
        goalWeight = goalWeight,
        goalType = goalType,
        fromUnit = fromUnit,
        toUnit = toUnit
    )
}
