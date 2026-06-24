package com.dmdbrands.gurus.weight.features.weightless.helper

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessFormControls
import kotlin.math.round

/**
 * Helper functions for Weightless-related operations.
 * Follows the exact same pattern as GoalHelper.
 */
object WeightlessHelper {
  /**
   * Safely converts a FormControl<String> to Double with a default value.
   */
  fun FormControl<String>.toDoubleSafe(default: Double = 0.0): Double =
    this.value.toDoubleOrNull() ?: default

  /**
   * Rounds a Double to one decimal place.
   */
  fun Double?.rounded(): Double? = this?.let { round(it * 10) / 10 }

  /**
   * Converts weight between units.
   */
  fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
    return when {
      from == to -> value
      from == WeightUnit.KG && (to == WeightUnit.LB || to == WeightUnit.LB_OZ) -> value * 2.20462
      (from == WeightUnit.LB || from == WeightUnit.LB_OZ) && to == WeightUnit.KG -> value / 2.20462
      // LB <-> LB_OZ share the same underlying pounds scale — no numeric conversion
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
   * Converts weightless weight from form controls for submission.
   * Uses the same pattern as GoalHelper.toGoal().
   */
  fun WeightlessFormControls.toWeightlessWeight(fromUnit: WeightUnit, toUnit: WeightUnit): Double? {
    val weightValue = this.weightlessWeight.toDoubleSafe()
    return if (weightValue > 0) {
      processWeight(weightValue, fromUnit, toUnit)
    } else {
      null
    }
  }

  /**
   * Processes stored weightless weight to display format.
   * Follows the same pattern as Goal.process().
   */
  fun processStoredWeightToDisplay(storedWeight: Double?, targetUnit: WeightUnit): Double {
    if (storedWeight == null) return 0.0

    // Convert from stored format (tenths of LB) to display format
    val baseWeight = storedWeight / 10.0

    // Convert to target unit if needed (stored weight is always in LB tenths)
    val convertedWeight = if (targetUnit == WeightUnit.KG) {
      baseWeight / 2.20462
    } else {
      baseWeight
    }
    // Round to one decimal place
    return round(convertedWeight * 10)
  }
}
