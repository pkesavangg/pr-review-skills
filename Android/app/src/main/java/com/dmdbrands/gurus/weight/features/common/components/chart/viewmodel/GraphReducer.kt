package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import kotlinx.collections.immutable.toImmutableList

/**
 * Reducer for the graph state, handling intents to update the graph state.
 */
class GraphReducer : IReducer<GraphState, GraphIntent> {
  override fun reduce(state: GraphState, intent: GraphIntent): GraphState? = when (intent) {
    is GraphIntent.UpdateGoal -> state.copy(
      goal = intent.goal,
    )

    is GraphIntent.UpdateData -> state.copy(
      data = intent.data.toImmutableList(),
    )

    is GraphIntent.SetSecondaryKey -> state.copy(
      secondaryKey = intent.key,
    )

    is GraphIntent.UpdateTarget -> state.copy(
      target = intent.target.toImmutableList(),
    )

    is GraphIntent.UpdatePrimaryYStep -> state.copy(primaryYStep = intent.step)

    is GraphIntent.UpdatePrimaryYAxis -> {
      val yStep = intent.yStep ?: state.primaryYStep
      state.copy(
        primaryYAxis = intent.yRangeValues,
        primaryYStep = yStep,
      )
    }

    is GraphIntent.UpdateMarkerIndex -> state.copy(markerIndex = intent.markerIndex)

    is GraphIntent.UpdateIsUpdating -> state.copy(isUpdating = intent.isUpdating)

    is GraphIntent.UpdateIsLoading -> state.copy(isLoading = intent.isLoading)

    is GraphIntent.UpdateIsSingleWindow -> state.copy(isSingleWindow = intent.isSingleWindow)

    is GraphIntent.UpdateIsEmptyGraph -> state.copy(isEmptyGraph = intent.isEmptyGraph)

    is GraphIntent.UpdateWeightUnit -> state.copy(
      weightUnit = intent.weightUnit,
    )

    is GraphIntent.ResetGraph -> state.copy(
      minTarget = null,
      maxTarget = null,
      markerIndex = null,
      isUpdating = false,
      isSingleWindow = false,
    )

    is GraphIntent.SetScrollRange -> state.copy(
      minTarget = intent.min,
      maxTarget = intent.max,
    )

    is GraphIntent.UpdateChartXRange -> state.copy(
      chartMinX = intent.minX,
      chartMaxX = intent.maxX,
    )

    else -> state
  }
}
