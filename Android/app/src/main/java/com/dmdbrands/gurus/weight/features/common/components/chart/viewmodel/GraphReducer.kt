package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer

/**
 * Reducer for the graph state, handling intents to update the graph state.
 */
class GraphReducer : IReducer<GraphState, GraphIntent> {
  override fun reduce(state: GraphState, intent: GraphIntent): GraphState? = when (intent) {
    is GraphIntent.UpdateGoal -> state.copy(
      goal = intent.goal,
    )

    is GraphIntent.UpdateData -> state.copy(
      data = intent.data,
    )

    is GraphIntent.UpdateSecondaryStat -> state.copy(
      secondaryKey = intent.stat,
    )

    is GraphIntent.UpdatePrimaryYStep -> state.copy(primaryYStep = intent.step)

    is GraphIntent.UpdatePrimaryYAxis -> state.copy(primaryYAxis = intent.yRangeValues)

    is GraphIntent.UpdateSecondaryYAxis -> state.copy(secondaryYAxis = intent.yRangeValues)

    is GraphIntent.UpdateMarkerIndex -> state.copy(markerIndex = intent.markerIndex)

    is GraphIntent.UpdateIsUpdating -> state.copy(isUpdating = intent.isUpdating)

    is GraphIntent.UpdateIsLoading -> state.copy(isLoading = intent.isLoading)

    is GraphIntent.UpdateIsEmptyGraph -> state.copy(isEmptyGraph = intent.isEmptyGraph)

    is GraphIntent.UpdateWeightUnit -> state.copy(
      weightUnit = intent.weightUnit,
    )

    is GraphIntent.ResetGraph -> state.copy(
      minTarget = null,
      maxTarget = null,
      markerIndex = null,
      isUpdating = false,
    )

    is GraphIntent.SetScrollRange -> state.copy(
      minTarget = intent.min,
      maxTarget = intent.max,
    )
  }
}
