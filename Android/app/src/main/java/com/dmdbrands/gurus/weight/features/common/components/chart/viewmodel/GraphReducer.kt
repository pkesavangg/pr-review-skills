package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import kotlinx.collections.immutable.toImmutableList

/**
 * Reducer for the graph state, handling intents to update the graph state.
 */
class GraphReducer : IReducer<GraphState, GraphIntent> {

  override fun reduce(state: GraphState, intent: GraphIntent): GraphState? = when (intent) {
    // ── Shared intents ──
    is GraphIntent.UpdateGoal -> state.copy(goal = intent.goal)
    is GraphIntent.SetSecondaryKey -> state.copy(secondaryKey = intent.key)
    is GraphIntent.UpdatePrimaryYStep -> state.copy(primaryYStep = intent.step)
    is GraphIntent.UpdatePrimaryYAxis -> state.copy(
      primaryYAxis = intent.yRangeValues,
      primaryYStep = intent.yStep ?: state.primaryYStep,
    )
    is GraphIntent.UpdateIsUpdating -> state.copy(isUpdating = intent.isUpdating)
    is GraphIntent.UpdateIsLoading -> state.copy(isLoading = intent.isLoading)
    is GraphIntent.UpdateWeightUnit -> state.copy(weightUnit = intent.weightUnit)
    is GraphIntent.ResetGraph -> state.copy(
      productStates = state.productStates.mapValues { (_, ps) ->
        ps.copy(
          minTarget = null,
          maxTarget = null,
          markerIndex = null,
          isSingleWindow = false,
        )
      },
    )

    // ── Product-scoped intents ──
    is GraphIntent.AddProductState -> {
      if (state.productStates.containsKey(intent.productType)) state
      else state.copy(productStates = state.productStates + (intent.productType to ProductGraphState()))
    }

    is GraphIntent.UpdateProductData -> state.updateProduct(intent.productType) {
      it.copy(data = intent.data.toImmutableList())
    }

    is GraphIntent.UpdateProductTarget -> state.updateProduct(intent.productType) {
      it.copy(target = intent.target.toImmutableList())
    }

    is GraphIntent.SetProductScrollRange -> state.updateProduct(intent.productType) {
      it.copy(minTarget = intent.min, maxTarget = intent.max)
    }

    is GraphIntent.UpdateProductMarkerIndex -> state.updateProduct(intent.productType) {
      it.copy(markerIndex = intent.markerIndex)
    }

    is GraphIntent.UpdateProductIsEmptyGraph -> state.updateProduct(intent.productType) {
      it.copy(isEmptyGraph = intent.isEmptyGraph)
    }

    is GraphIntent.UpdateProductIsSingleWindow -> state.updateProduct(intent.productType) {
      it.copy(isSingleWindow = intent.isSingleWindow)
    }

    is GraphIntent.UpdateProductChartXRange -> state.updateProduct(intent.productType) {
      it.copy(chartMinX = intent.minX, chartMaxX = intent.maxX)
    }

    else -> state
  }
}

/** Helper to update a single product's state within the map. */
private fun GraphState.updateProduct(
  productType: ProductType,
  update: (ProductGraphState) -> ProductGraphState,
): GraphState {
  val current = productStates[productType] ?: ProductGraphState()
  return copy(productStates = productStates + (productType to update(current)))
}
