package com.dmdbrands.gurus.weight.features.goal.helper

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.goal.model.GoalFormControls
import kotlin.math.round

/**
 * Helper functions for Goal-related operations.
 */
object GoalHelper {
  /**
   * Safely converts a FormControl<String> to Double with a default value.
   */
  fun FormControl<String>.toDoubleSafe(default: Double = 0.0): Double =
    this.value.toDoubleOrNull() ?: default

  /**
   * Goal + entry weights are represented as tenths (e.g. "1521" -> 152.1).
   * This converts a "tenths" value into a normal display value.
   */
  private fun toDisplayFromTenths(value: Double): Double = value / 10.0

  /**
   * Core function to create a Goal with proper weight conversion.
   * Used by both form submission and signup flows.
   */
  private fun createGoalInternal(
    startingWeight: Double,
    goalWeight: Double,
    goalType: String,
    fromUnit: WeightUnit,
    toUnit: WeightUnit
  ): Goal {
    // Inputs come from UI form controls (and signup) in "tenths" format.
    // Convert tenths -> display -> unit convert -> back to tenths for API/storage.
    val processedStartingWeight =
      processWeight(
        weightTenths = startingWeight,
        fromUnit = fromUnit,
        toUnit = toUnit,
      )
    val processedGoalWeight =
      processWeight(
        weightTenths = goalWeight,
        fromUnit = fromUnit,
        toUnit = toUnit,
      )
    // Determine specific goal type
    val specificGoalType = if (goalType == GoalType.MAINTAIN.value) {
      "maintain"
    } else {
      if (processedGoalWeight <= processedStartingWeight) "lose" else "gain"
    }

    return Goal(
      goalWeight = processedGoalWeight,
      initialWeight = processedStartingWeight,
      type = specificGoalType,
    )
  }

  /**
   * Converts weight between units.
   */
  fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
    return when {
      from == to -> value
      from == WeightUnit.KG && (to == WeightUnit.LB || to == WeightUnit.LB_OZ) -> ConversionTools.kgToLbs(value)
      (from == WeightUnit.LB || from == WeightUnit.LB_OZ) && to == WeightUnit.KG -> ConversionTools.lbsToKg(value)
      // LB <-> LB_OZ share the same underlying pounds scale — no numeric conversion
      else -> value
    }
  }

  /**
   * Processes weight with unit conversion and rounding.
   */
  fun processWeight(weightTenths: Double, fromUnit: WeightUnit, toUnit: WeightUnit): Double {
    val displayWeight = toDisplayFromTenths(weightTenths)
    val convertedDisplayWeight = convertWeight(displayWeight, fromUnit, toUnit)
    // Stored format is tenths in the target unit, represented as a whole number.
    return round(convertedDisplayWeight * 10)
  }

  /**
   * Creates a Goal model from form controls.
   * Uses the core createGoalInternal function.
   */
  fun GoalFormControls.toGoal(
    fromUnit: WeightUnit,
    toUnit: WeightUnit,
  ): Goal {
    return createGoalInternal(
      startingWeight = this.startingWeight.toDoubleSafe(),
      goalWeight = this.goalWeight.toDoubleSafe(),
      goalType = this.goalType.value,
      fromUnit = fromUnit,
      toUnit = toUnit,
    )
  }

  /**
   * Creates a Goal model from signup data.
   * Uses the same core createGoalInternal function as form submission.
   */
  fun createGoal(
    startingWeight: Double,
    goalWeight: Double,
    goalType: String,
    fromUnit: WeightUnit,
    toUnit: WeightUnit
  ): Goal = createGoalInternal(
    startingWeight = startingWeight,
    goalWeight = goalWeight,
    goalType = goalType,
    fromUnit = fromUnit,
    toUnit = toUnit,
  )
}
