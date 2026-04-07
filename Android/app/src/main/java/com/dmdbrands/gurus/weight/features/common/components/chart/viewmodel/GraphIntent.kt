package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.CartesianRangeValues
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey

/**
 * Intent for graph actions, defining all possible user interactions and state updates.
 */
sealed interface GraphIntent : IReducer.Intent {

  // ── Shared (global) intents ──

  data class UpdateGoal(val goal: Goal?) : GraphIntent
  data class UpdateWeightUnit(val weightUnit: WeightUnit) : GraphIntent
  data class SetSecondaryKey(val key: DashboardKey?) : GraphIntent
  data class UpdatePrimaryYStep(val step: Double) : GraphIntent
  data class UpdatePrimaryYAxis(val yRangeValues: CartesianRangeValues, val yStep: Double?) : GraphIntent
  data class UpdateIsUpdating(val isUpdating: Boolean) : GraphIntent
  data class UpdateIsLoading(val isLoading: Boolean) : GraphIntent
  object ResetGraph : GraphIntent

  // ── Product-scoped intents (operate on productStates[productType]) ──

  /** Add a new product entry to the map (lazy init). No-op if already exists. */
  data class AddProductState(val productType: ProductType) : GraphIntent

  /** Update data for a specific product. */
  data class UpdateProductData(val productType: ProductType, val data: List<PeriodBodyScaleSummary>) : GraphIntent

  /** Update target entries for a specific product. */
  data class UpdateProductTarget(val productType: ProductType, val target: List<PeriodBodyScaleSummary>) : GraphIntent

  /** Update scroll range for a specific product. */
  data class SetProductScrollRange(
    val productType: ProductType,
    val min: Long,
    val max: Long,
    val onFallback: () -> Unit = {},
  ) : GraphIntent

  /** Update marker index for a specific product. */
  data class UpdateProductMarkerIndex(val productType: ProductType, val markerIndex: Double?) : GraphIntent

  /** Update empty graph flag for a specific product. */
  data class UpdateProductIsEmptyGraph(val productType: ProductType, val isEmptyGraph: Boolean) : GraphIntent

  /** Update single window flag for a specific product. */
  data class UpdateProductIsSingleWindow(val productType: ProductType, val isSingleWindow: Boolean) : GraphIntent

  /** Update chart X range for a specific product. */
  data class UpdateProductChartXRange(val productType: ProductType, val minX: Double, val maxX: Double) : GraphIntent

  /** Update visible Y range from ScrollAwareRangeProvider. */
  data class UpdateVisibleYRange(val minY: Double, val maxY: Double, val minX: Double, val maxX: Double) : GraphIntent
}
