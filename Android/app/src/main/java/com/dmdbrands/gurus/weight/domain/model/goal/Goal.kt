package com.dmdbrands.gurus.weight.domain.model.goal

import com.dmdbrands.gurus.weight.domain.model.common.IUnitProcessable
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import kotlin.math.round

/**
 * Data class representing a user's weight goal.
 */
data class Goal(
  val goalWeight: Double,
  val initialWeight: Double,
  val type: String,
  val goalType: String? = null,
  val metPreviousGoal: Boolean = false,
  val percent: Double? = 0.0,
  val account: Account? = null,
) : IUnitProcessable<Goal> {

  override fun process(unit: WeightUnit?, weightLess: Weightless?): Goal {
    // Convert from stored format (LB) to display format
    val baseGoalWeight = goalWeight / 10.0
    val baseInitialWeight = initialWeight / 10.0

    // Convert to target unit if needed (stored weight is always in LB)
    val convertedGoalWeight = if (unit == WeightUnit.KG) {
      baseGoalWeight / 2.20462
    } else {
      baseGoalWeight
    }

    val convertedInitialWeight = if (unit == WeightUnit.KG) {
      baseInitialWeight / 2.20462
    } else {
      baseInitialWeight
    }

    // Round to one decimal place
    val roundedGoalWeight = round(convertedGoalWeight * 10)
    val roundedInitialWeight = round(convertedInitialWeight * 10)
    return copy(
      goalWeight = roundedGoalWeight,
      initialWeight = roundedInitialWeight,
    )
  }
}
