package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer

/**
 * Reducer for the graph state, handling intents to update the graph state.
 */
class GraphReducer : IReducer<GraphState, GraphIntent> {
  override fun reduce(state: GraphState, intent: GraphIntent): GraphState? = when (intent) {
    is GraphIntent.InitializeGraph ->
      state.copy(
        graphLines = intent.graphLines,
        secondaryGraphLines = intent.secondaryGraphLines,
        goal = intent.goal,
      )

    is GraphIntent.UpdateTargetRange -> state.copy(
      minTarget = intent.minTarget,
      maxTarget = intent.maxTarget,
    )

    is GraphIntent.UpdatePrimaryYAxis -> state.copy(primaryYAxis = intent.axisMeta)

    is GraphIntent.UpdateSecondaryYAxis -> state.copy(secondaryYAxis = intent.axisMeta)

    is GraphIntent.UpdateMarkerIndex -> state.copy(markerIndex = intent.markerIndex)

    is GraphIntent.UpdateSavedTarget -> state.copy(savedTarget = intent.target)

    is GraphIntent.UpdateIsUpdating -> state.copy(isUpdating = intent.isUpdating)

    is GraphIntent.UpdateScrollValue -> state.copy(scrollValue = intent.scrollValue)

    is GraphIntent.UpdateSeparators -> state.copy(separators = intent.separators)

    is GraphIntent.UpdateComputationJob -> state.copy(computationJob = intent.job)

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

    is GraphIntent.SetScrollTarget -> state.copy(scrollTarget = intent.target)

    else -> state
  }
}
