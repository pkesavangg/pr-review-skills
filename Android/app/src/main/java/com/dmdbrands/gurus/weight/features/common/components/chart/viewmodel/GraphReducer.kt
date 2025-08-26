package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer

/**
 * Reducer for the graph state, handling intents to update the graph state.
 */
class GraphReducer : IReducer<GraphState, GraphIntent> {
    override fun reduce(state: GraphState, intent: GraphIntent): GraphState? = when (intent) {
        is GraphIntent.InitializeGraph -> state.copy(
            graphLines = intent.graphLines,
            secondaryGraphLines = intent.secondaryGraphLines,
            segment = intent.segment,
            goal = intent.goal
        )

        is GraphIntent.UpdateSegment -> state.copy(segment = intent.segment)

        is GraphIntent.UpdateSelectedData -> state.copy(selectedData = intent.selectedData)

        is GraphIntent.UpdatePoint -> state.copy(point = intent.point)

        is GraphIntent.UpdateTargetRange -> state.copy(
            minTarget = intent.minTarget,
            maxTarget = intent.maxTarget
        )

        is GraphIntent.UpdateYAxisTargets -> state.copy(
            minYTarget = intent.minYTarget,
            maxYTarget = intent.maxYTarget,
            secondaryMinYTarget = intent.secondaryMinYTarget,
            secondaryMaxYTarget = intent.secondaryMaxYTarget
        )

        is GraphIntent.UpdateSelectedTarget -> state.copy(selectedTarget = intent.selectedTarget)

        is GraphIntent.UpdateMarkerIndex -> state.copy(markerIndex = intent.markerIndex)

        is GraphIntent.UpdateIsUpdating -> state.copy(isUpdating = intent.isUpdating)

        is GraphIntent.UpdateStepSize -> state.copy(stepSize = intent.stepSize)

        is GraphIntent.UpdateScrollState -> state.copy(scrollState = intent.scrollState)

        is GraphIntent.UpdateInitialTimestamp -> state.copy(initialTimeStamp = intent.initialTimestamp)

        is GraphIntent.UpdateTodayMills -> state.copy(todayMills = intent.todayMills)

        is GraphIntent.UpdateRangeValues -> state.copy(
            startRangeX = intent.startRangeX,
            endRangeX = intent.endRangeX
        )

        is GraphIntent.UpdateSeparators -> state.copy(separators = intent.separators)

        is GraphIntent.UpdateIsEmpty -> state.copy(isEmpty = intent.isEmpty)

        is GraphIntent.UpdateComputationJob -> state.copy(computationJob = intent.job)

        is GraphIntent.UpdateGraphKey -> state.copy(graphKey = intent.graphKey)

        is GraphIntent.UpdateXLabels -> state.copy(xLabels = intent.xLabels)

        is GraphIntent.UpdateYSeries -> state.copy(ySeries = intent.ySeries)

        is GraphIntent.UpdateTimestamps -> state.copy(timeStamp = intent.timeStamp)

        is GraphIntent.ResetGraph -> state.copy(
            selectedData = emptyList(),
            point = null,
            minTarget = null,
            maxTarget = null,
            selectedTarget = null,
            markerIndex = null,
            isUpdating = false,
            computationJob = null
        )

        is GraphIntent.HandleScroll -> state.copy(
            minTarget = intent.min,
            maxTarget = intent.max
        )

        is GraphIntent.HandleMarkerSelection -> state.copy(selectedData = intent.selectedData)
    }
}
