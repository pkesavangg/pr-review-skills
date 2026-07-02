package com.dmdbrands.gurus.weight.features.common.helper

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import kotlin.math.round

/**
 * Helper object for Account-related operations and conversions.
 */
object AccountHelper {

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
   * Processes stored weightless weight to display format.
   * Follows the same pattern as Goal.process().
   */
  fun processStoredWeightToDisplay(storedWeight: Double?, targetUnit: WeightUnit): Double {
    if (storedWeight == null) return 0.0

    // Convert from stored format (tenths of LB) to display format
    val baseWeight = storedWeight / 10.0

    // Convert to target unit if needed (stored weight is always in LB tenths)
    val convertedWeight = if (targetUnit == WeightUnit.KG) {
      ConversionTools.lbsToKg(baseWeight)
    } else {
      baseWeight
    }
    // Round to one decimal place
    return round(convertedWeight * 10) / 10
  }

  /**
   * Checks if the account uses metric units. Delegates to the canonical
   * [Account.isMetric] so there is a single source of truth.
   *
   * @return true if metric (kg), false if imperial (lbs)
   */
  fun Account.isMetricUnit(): Boolean = this.isMetric

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
