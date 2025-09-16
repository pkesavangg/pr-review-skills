package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.ONE_DAY_MILLIS
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.averageYValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.filterXValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.intervalCount
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import android.icu.util.Calendar

/**
 * ViewModel for the graph component, managing chart state and business logic.
 * Follows MVI pattern with clear separation of concerns.
 */
@HiltViewModel(
  assistedFactory = GraphViewModel.Factory::class,
)
class GraphViewModel @AssistedInject constructor(
  @Assisted val segment: GraphSegment
) : BaseIntentViewModel<GraphState, GraphIntent>(
  reducer = GraphReducer(),
) {

  @AssistedFactory
  interface Factory {
    fun create(segment: GraphSegment): GraphViewModel
  }

  private var hasInitialized = false
  private var onMetricUpdate: (List<GraphPoint>) -> Unit = {}
  private var onScroll: (String?) -> Unit = {}
  private var onLabelUpdate: (String) -> Unit = {}
  private var onScrollValueUpdate: suspend (Double?) -> Unit = {}
  private var currentModelProducerJob: Job? = null

  override fun provideInitialState(): GraphState = GraphState()

  override fun onCleared() {
    super.onCleared()
    // Cancel any running model producer job
    currentModelProducerJob?.cancel()
  }

  override fun handleIntent(intent: GraphIntent) {
    super.handleIntent(intent)
    when (intent) {
      is GraphIntent.InitializeGraph -> initializeGraph(intent)
      is GraphIntent.UpdateMarkerIndex -> handleSelectedDataUpdate()
      is GraphIntent.SetScrollRange -> handleScroll(intent.min, intent.max)
      else -> null
    }
  }

  /**
   * Initializes the graph with new data and sets up initial state.
   */
  private fun initializeGraph(intent: GraphIntent.InitializeGraph) {
    super.handleIntent(intent)

    val graphLines = intent.graphLines.first()
    val secondaryGraphLines = intent.secondaryGraphLines
    val goal = intent.goal

    // Setup chart model producer
    setupChartModelProducer(graphLines, secondaryGraphLines, goal)
  }

  /**
   * Sets up the chart model producer with primary and secondary graph lines.
   */
  private fun setupChartModelProducer(
    graphLines: GraphLine,
    secondaryGraphLines: GraphLine?,
    goal: Goal?
  ) {
    // Cancel any existing model producer job
    currentModelProducerJob?.cancel()

    val currentState = state.value
    val xLabels = currentState.xLabels
    val ySeries = currentState.yLabels

    currentModelProducerJob = viewModelScope.launch {

      // Check if job is still active before running transaction
      if (isActive) {
        currentState.modelProducer.runTransaction {
          lineSeries {
            ySeries.forEach { y ->
              series(
                x = xLabels.map { it.value as Long },
                y = y.map { it.value },
                ranges = calculateInitialYRange(graphLines, goal),
              )
            }
          }

          if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
            lineSeries {
              series(
                x = secondaryGraphLines.points.map { it.x.value as Long },
                y = secondaryGraphLines.points.map { it.y.value },
                ranges = calculateInitialYRange(secondaryGraphLines, goal),
              )
            }
          }
        }
      }
    }
    handleIntent(GraphIntent.SetScrollTarget(target = xLabels.maxOfOrNull { it.value.toDouble() }))
  }

  private fun calculateInitialYRange(graphLines: GraphLine, goal: Goal? = null): CartesianRangeValues {
    // Get the end and start timestamp
    val endX = _state.value.endTimeStamp ?: Calendar.getInstance().timeInMillis
    val startX = GraphUtil.getStartRange(segment, endX) ?: Calendar.getInstance().timeInMillis
    // calculate with the interval count to get padded range
    val intervalCount = segment.intervalCount().div(2)
    val paddedStartX = startX.minus(ONE_DAY_MILLIS * intervalCount)
    val paddedEndX = endX.plus(ONE_DAY_MILLIS * intervalCount)

    val paddedGraphLines = filterXValuesInRange(
      listOf(graphLines),
      paddedStartX,
      paddedEndX,
    )

    val paddedYAxis = paddedGraphLines.flatMap { graphLine ->
      graphLine.points.map { it.y.value.toDouble() }
    }

    if (paddedYAxis.isNotEmpty()) {
      val graphMeta = generateNiceScale(
        floor(paddedYAxis.min()),
        ceil(paddedYAxis.max()),
        goalWeight = goal?.goalWeight ?: 0.0,
      )
      return CartesianRangeValues(
        minY = graphMeta.min,
        maxY = graphMeta.max,
        maxX = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)?.toDouble(),
        minX = GraphUtil.getStartRange(segment, _state.value.initialTimeStamp)?.toDouble(),
      )
    }
    return CartesianRangeValues(
      maxX = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)?.toDouble(),
      minX = GraphUtil.getStartRange(segment, _state.value.initialTimeStamp)?.toDouble(),
    )
  }

  /**
   * Handles updates to selected data and triggers metric updates.
   */
  private fun handleSelectedDataUpdate() {
    val currentState = state.value
    val selectedData = currentState.selectedData

    // Cancel any existing computation job
    currentState.computationJob?.cancel()

    if (selectedData.isEmpty()) {
      val job = viewModelScope.launch(Dispatchers.Default) {
        val formattedRange = GraphUtil.formatDateRange(
          currentState.minTarget ?: 0L,
          currentState.maxTarget ?: 0L,
          segment,
        )
        onScroll(formattedRange)

        val subset = averageYValuesInRange(
          currentState.graphLines,
          currentState.minTarget ?: 0L,
          currentState.maxTarget ?: 0L,
        )

        if (isActive) {
          val joinedLabel = subset.values
            .filterNotNull()
            .joinToString(" / ") { it.label }
          onLabelUpdate(joinedLabel)

          val graphLines = filterXValuesInRange(
            currentState.graphLines,
            currentState.minTarget ?: 0L,
            currentState.maxTarget ?: 0L,
          )
          onMetricUpdate(graphLines.flatMap { it.points })
        }

        super.handleIntent(GraphIntent.UpdateComputationJob(null))
      }
      super.handleIntent(GraphIntent.UpdateComputationJob(job))
    } else {
      onScroll(null)
      onMetricUpdate(listOf(selectedData.first()))
      onLabelUpdate(selectedData.first().y.label)
    }
  }

  /**
   * Handles scroll events and updates the visible range.
   */
  private fun handleScroll(min: Long, max: Long) {
    val currentState = state.value

    // Cancel any existing computation job
    currentState.computationJob?.cancel()

    val job = viewModelScope.launch(Dispatchers.IO) {
      val formattedRange = GraphUtil.formatDateRange(min, max, segment)
      onScroll(formattedRange)

      val subset = averageYValuesInRange(
        currentState.graphLines,
        min,
        max,
      )

      super.handleIntent(GraphIntent.UpdateMarkerIndex(null))
      super.handleIntent(GraphIntent.UpdateSavedTarget(max))

      if (isActive) {
        val joinedLabel = subset.values
          .filterNotNull()
          .joinToString(" / ") { it.label }
        onLabelUpdate(joinedLabel)

        val graphLines = filterXValuesInRange(
          currentState.graphLines,
          min,
          max,
        )
        onMetricUpdate(graphLines.flatMap { it.points })

        // Calculate Y-axis targets
        val intervalCount = segment.intervalCount().div(2)
        val paddedMinTarget = min.minus(ONE_DAY_MILLIS * intervalCount)
        val paddedMaxTarget = max.plus(ONE_DAY_MILLIS * intervalCount)

        val paddedGraphLines = filterXValuesInRange(
          currentState.graphLines,
          paddedMinTarget,
          paddedMaxTarget,
        )

        val yAxis = paddedGraphLines.flatMap { graphLine ->
          graphLine.points.map { it.y.value as Double }
        }

        if (yAxis.isNotEmpty()) {
          var tempMax = ceil(yAxis.max())
          var tempMin = floor(yAxis.min())

          if (currentState.primaryYAxis?.max == currentState.primaryYAxis?.min) {
            tempMax += 1
            tempMin -= 1
          }

          val graphMeta = generateNiceScale(
            tempMin,
            tempMax,
            goalWeight = currentState.goal?.goalWeight ?: 0.0,
          )

          handleIntent(
            GraphIntent.UpdatePrimaryYAxis(
              axisMeta = graphMeta,
            ),
          )
          if (currentState.secondaryGraphLines != null) {
            calculateInitialYRange(currentState.secondaryGraphLines)
          }
        }
      }

      handleIntent(GraphIntent.UpdateComputationJob(null))
    }

    handleIntent(GraphIntent.UpdateComputationJob(job))
  }

  /**
   * Sets the callback functions for the graph.
   */
  fun setCallbacks(
    onMetricUpdate: (List<GraphPoint>) -> Unit,
    onScroll: (String?) -> Unit,
    onLabelUpdate: (String) -> Unit,
    scrollToValue: suspend (Double?) -> Unit
  ) {
    this.onMetricUpdate = onMetricUpdate
    this.onScroll = onScroll
    this.onLabelUpdate = onLabelUpdate
    this.onScrollValueUpdate = scrollToValue
  }
}
