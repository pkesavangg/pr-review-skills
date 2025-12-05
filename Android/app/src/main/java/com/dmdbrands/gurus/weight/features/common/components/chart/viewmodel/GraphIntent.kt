package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues

/**
 * Intent for graph actions, defining all possible user interactions and state updates.
 */
sealed interface GraphIntent : IReducer.Intent {

  data class UpdateData(val data: List<PeriodBodyScaleSummary>) : GraphIntent

  data class UpdateTarget(val target: List<PeriodBodyScaleSummary>) : GraphIntent

  data class SetSecondaryKey(val key: DashboardKey?) : GraphIntent

  data class UpdateGoal(val goal: Goal?) : GraphIntent

  data class UpdatePrimaryYStep(val step: Double) : GraphIntent

  data class UpdateWeightUnit(val weightUnit: WeightUnit) : GraphIntent

  data class UpdateIsEmptyGraph(val isEmptyGraph: Boolean) : GraphIntent

  /** Update primary Y-axis */
  data class UpdatePrimaryYAxis(val yRangeValues: CartesianRangeValues) : GraphIntent

  /**
   * Update cached primary Y-axis range.
   * Used for iOS-style renormalization: cache Y-axis domain on scroll end,
   * then trigger renormalization when domain changes.
   */
  data class UpdateCachedPrimaryYAxis(val yRangeValues: CartesianRangeValues) : GraphIntent

  /** Update marker index */
  data class UpdateMarkerIndex(val markerIndex: Double?) : GraphIntent

  /** Update updating state */
  data class UpdateIsUpdating(val isUpdating: Boolean) : GraphIntent

  /** Update loading state */
  data class UpdateIsLoading(val isLoading: Boolean) : GraphIntent

  /** Reset graph state */
  object ResetGraph : GraphIntent

  data class SetScrollRange(val min: Long, val max: Long, val onFallback: () -> Unit = {}) : GraphIntent
}
