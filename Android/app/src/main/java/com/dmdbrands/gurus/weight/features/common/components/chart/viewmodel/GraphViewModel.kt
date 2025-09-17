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

  private var onTargetUpdate: (List<Double>, List<Double>) -> Unit = { _, _ -> }
  private var onRangeUpdate: (String?) -> Unit = { }
  private var onWeightLabelUpdate: (String) -> Unit = { }
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
                ranges = calculateYAxisRange(graphLines, goal),
              )
            }
          }

          if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
            lineSeries {
              series(
                x = secondaryGraphLines.points.map { it.x.value as Long },
                y = secondaryGraphLines.points.map { it.y.value },
                ranges = calculateYAxisRange(secondaryGraphLines, goal),
              )
            }
          }
        }
      }
    }
  }

  private fun calculateYAxisRange(
    graphLines: GraphLine,
    goal: Goal? = null,
    min: Long? = null,
    max: Long? = null,
    onInit: Boolean = true
  ): CartesianRangeValues {
    // Get the end and start timestamp
    val initialTimestamp = graphLines.points.minOfOrNull { it.x.value.toLong() }
    val endTimeStamp = graphLines.points.maxOfOrNull { it.x.value.toLong() }
    val endX = max ?: _state.value.maxTarget ?: endTimeStamp ?: Calendar.getInstance().timeInMillis
    val startX =
      min ?: _state.value.minTarget ?: if (segment != GraphSegment.TOTAL)
        GraphUtil.getStartRange(segment, endX) ?: Calendar.getInstance().timeInMillis
      else
        initialTimestamp ?: Calendar.getInstance().timeInMillis
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
        maxX = if (onInit) GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)?.toDouble() else null,
        minX = if (onInit) GraphUtil.getStartRange(segment, initialTimestamp)?.toDouble() else null,
      )
    }
    return CartesianRangeValues(
      maxX = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)?.toDouble(),
      minX = GraphUtil.getStartRange(segment, initialTimestamp)?.toDouble(),
    )
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
      onRangeUpdate(formattedRange)


      if (isActive) {
        this@GraphViewModel.updateWeightLabel(min, max)
        val graphLines = filterXValuesInRange(
          currentState.graphLines,
          min,
          max,
        )
        val currentRangeTimeStamps = graphLines.flatMap { it.points.map { it.x.value.toDouble() } }
        onTargetUpdate(currentRangeTimeStamps, emptyList())
        val primaryYAxis = calculateYAxisRange(
          currentState.graphLines.first(),
          goal = currentState.goal,
          onInit = false,
          min = min,
          max = max,
        )
        super.handleIntent(
          GraphIntent.UpdatePrimaryYAxis(yRangeValues = primaryYAxis),
        )
        if (currentState.secondaryGraphLines != null) {
          val secondaryGraphLines =
            calculateYAxisRange(currentState.secondaryGraphLines, onInit = false, min = min, max = max)
          super.handleIntent(
            GraphIntent.UpdateSecondaryYAxis(yRangeValues = secondaryGraphLines),
          )
        }
      }

      handleIntent(GraphIntent.UpdateComputationJob(null))
    }

    handleIntent(GraphIntent.UpdateComputationJob(job))
  }

  private fun updateWeightLabel(min: Long, max: Long) {
    val subset = averageYValuesInRange(
      _state.value.graphLines,
      min,
      max,
    )
    val joinedLabel = subset.values
      .filterNotNull()
      .joinToString(" / ") { it.label }
    onWeightLabelUpdate(joinedLabel)
  }

  /**
   * Sets the callback functions for the graph.
   */
  fun setCallbacks(
    onTargetUpdate: (List<Double>, List<Double>) -> Unit,
    onRangeUpdate: (String?) -> Unit,
    onWeightLabelUpdate: (String) -> Unit,
  ) {
    this.onTargetUpdate = onTargetUpdate
    this.onRangeUpdate = onRangeUpdate
    this.onWeightLabelUpdate = onWeightLabelUpdate
  }
}
