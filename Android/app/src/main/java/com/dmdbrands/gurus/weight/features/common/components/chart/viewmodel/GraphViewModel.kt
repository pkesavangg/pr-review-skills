package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.filterXValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  @Assisted val segment: GraphSegment,
  private val dashboardService: IDashboardService,
  private val goalService: IGoalService,
  private val entryService: IEntryService,
  private val accountService: IAccountService
) : BaseIntentViewModel<GraphState, GraphIntent>(
  reducer = GraphReducer(),
) {

  override fun handleIntent(intent: GraphIntent) {
    super.handleIntent(intent)
    when (intent) {
      is GraphIntent.SetScrollRange -> handleScroll(intent.min, intent.max, intent.onFallback)
      else -> null
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(segment: GraphSegment): GraphViewModel
  }

  private val dataFlow = if (segment == GraphSegment.WEEK || segment == GraphSegment.MONTH) {
    entryService.daywiseBodyScaleAverages
  } else {
    entryService.monthlyBodyScaleAverages
  }

  private var currentModelProducerJob: Job? = null
  private var scrollDebounceJob: Job? = null

  init {
    observeDataChanges()
    subscribeWeightUnit()
    initializeWeightUnit()
  }

  private fun subscribeWeightUnit() {
    viewModelScope.launch {
      accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().collect { weightUnit ->
        if (weightUnit != null)
          handleIntent(
            GraphIntent.UpdateWeightUnit(weightUnit),
          )
      }
    }
  }

  /**
   * Initializes the weight unit from the current account settings immediately.
   * This ensures the correct unit is displayed on app launch.
   */
  private fun initializeWeightUnit() {
    viewModelScope.launch {
      try {
        val currentAccount = accountService.activeAccountFlow.first()
        val weightUnit = currentAccount?.weightUnit
        if (weightUnit != null) {
          handleIntent(GraphIntent.UpdateWeightUnit(weightUnit))
        }
      } catch (e: Exception) {
        // Log error but don't crash - fallback to default KG
        android.util.Log.w("GraphViewModel", "Failed to initialize weight unit, using default KG", e)
      }
    }
  }

  private fun observeDataChanges() {
    viewModelScope.launch {
      combine(
        dataFlow,
        dashboardService.selectedKey,
        goalService.getCurrentGoal(),
      ) { data, secondaryKey, goal ->
        handleIntent(GraphIntent.UpdateData(data))
        handleIntent(GraphIntent.UpdateGoal(goal))
        handleIntent(GraphIntent.SetSecondaryKey(secondaryKey))
        initializeGraph(data, goal, secondaryKey = secondaryKey)
      }.launchIn(viewModelScope)
    }
  }

  /**
   * Initializes the graph with new data and sets up initial state.
   */
  private fun initializeGraph(
    data: List<PeriodBodyScaleSummary>? = null,
    goal: Goal? = null,
    secondaryKey: DashboardKey? = null
  ) {
    viewModelScope.launch {
      val data = data ?: _state.value.data
      val goal = goal ?: _state.value.goal
      val secondaryKey = secondaryKey ?: _state.value.secondaryKey
      scrollDebounceJob?.cancel()

      // Setup chart model producer
      if (data.isNotEmpty())
        setupChartModelProducer(data, secondaryKey, goal)
      else
        setupEmptyModelProducer(goal)
    }
  }

  /**
   * Sets up an empty chart model producer when no data is available.
  Sets up an empty chart model producer when no data is available.
   */
  private fun setupEmptyModelProducer(goal: Goal?) {
    val currentState = state.value
    // Cancel any existing model producer job
    currentModelProducerJob?.cancel()

    // Set empty model producer
    currentModelProducerJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        // Check if job is still active before running transaction
        if (isActive) {
          val isWeightlessMode = accountService.activeAccountFlow.first()?.isWeightlessOn == true

          handleIntent(GraphIntent.UpdateIsEmptyGraph(isEmptyGraph = true))
          val graphMeta = if (goal != null) generateNiceScale(
            goal.goalWeight.div(10.0) - 10.0,
            goal.goalWeight.div(10.0) + 10.0,
            goal.goalWeight.div(10.0),
            isWeightLessMode = isWeightlessMode,
          ) else null

          if (graphMeta != null) {
            handleIntent(GraphIntent.UpdatePrimaryYStep(graphMeta.step))
          }
          currentState.modelProducer.runTransaction {
            lineSeries {
              series(
                listOf(0.0), listOf(0.0),
                ranges = CartesianRangeValues(
                  minY = graphMeta?.min ?: 2.0,
                  maxY = graphMeta?.max ?: 3.0,
                  minX = GraphUtil.getStartRange(segment, Calendar.getInstance().timeInMillis)?.toDouble(),
                  maxX = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)?.toDouble(),
                ),
              )
            }
          }
        }
      } catch (e: Exception) {
        // Log error but don't crash the UI
        android.util.Log.e("GraphViewModel", "Error setting up empty chart model producer", e)
        // Clear loading state on error
        withContext(Dispatchers.Main) {
          super.handleIntent(GraphIntent.UpdateIsLoading(false))
        }
      }
    }
  }

  /**
   * Sets up the chart model producer with primary and secondary graph lines.
   * Optimized to run heavy computations on background thread.
   */
  private fun setupChartModelProducer(
    data: List<PeriodBodyScaleSummary>,
    secondaryKey: DashboardKey? = null,
    goal: Goal?
  ) {
    // Cancel any existing model producer job
    currentModelProducerJob?.cancel()



    currentModelProducerJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val currentState = state.value
        val graphLines = data.getWeightGraphPoints()
        val secondaryStat = secondaryKey ?: _state.value.secondaryKey
        val secondaryGraphLines = secondaryStat?.let { data.toGraphPoints((it as DashboardKey.Metric).key) }
        val xLabels = graphLines.points.map { point -> point.x }
        val ySeries = graphLines.points.map { it.y }
        val initialTimeStamp = graphLines.points.minOfOrNull { it.x.value.toLong() }
        val endTimeStamp = graphLines.points.maxOfOrNull { it.x.value.toLong() }
        val calendar = Calendar.getInstance()

        val (startX, endX) = if (segment == GraphSegment.TOTAL) {
          val start = (initialTimeStamp ?: calendar.timeInMillis).let {
            Calendar.getInstance().apply { timeInMillis = it; add(Calendar.MONTH, -6) }.timeInMillis
          }
          val end = (endTimeStamp ?: calendar.timeInMillis).let {
            Calendar.getInstance().apply { timeInMillis = it; add(Calendar.MONTH, +6) }.timeInMillis
          }
          start to end
        } else {
          val start = _state.value.minTarget ?: GraphUtil.getStartRange(
            segment,
            endTimeStamp,
          ) ?: calendar.timeInMillis

          val end = _state.value.maxTarget ?: GraphUtil.getEndRange(
            segment,
            endTimeStamp,
          ) ?: calendar.timeInMillis

          start to end
        }

        handleIntent(GraphIntent.UpdateIsEmptyGraph(isEmptyGraph = false))
        super.handleIntent(GraphIntent.SetScrollRange(startX, endX))
        val filteredData = data.filter {
          it.getTimeStamp() in startX..endX
        }
        if (filteredData.isNotEmpty())
          super.handleIntent(GraphIntent.UpdateTarget(filteredData))

        // Get weightless mode before entering transaction
        val isWeightlessMode = accountService.activeAccountFlow.first()?.isWeightlessOn == true

        // Pre-calculate Y-axis ranges on background thread
        val primaryYAxisRange =
          calculateYAxisRange(graphLines, goal, isWeightlessMode = isWeightlessMode, min = startX, max = endX)
        val secondaryYAxisRange = if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
          handleIntent(
            GraphIntent.UpdateSecondaryYAxis(
              yRangeValues = CartesianRangeValues(minY = null, maxY = null),
            ),
          )
          calculateYAxisRange(
            secondaryGraphLines,
            goal,
            isWeightlessMode = isWeightlessMode,
            isSecondary = true,
            min = startX,
            max = endX,
          )
        } else null

        // Pre-calculate series data on background thread
        val primaryXData = xLabels.map { it.value as Long }
        val primaryYData = ySeries.map { it.value as Double }

        // Check if job is still active before running transaction
        if (isActive) {
          // Switch to main thread for UI updates
          currentState.modelProducer.runTransaction {
            lineSeries {
              series(
                x = primaryXData,
                y = primaryYData,
                ranges = primaryYAxisRange,
              )
            }
            if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
              val secondaryXData = secondaryGraphLines.points.map { it.x.value as Long }
              val secondaryYData = secondaryGraphLines.points.map { it.y.value }
              lineSeries {
                series(
                  x = secondaryXData,
                  y = secondaryYData,
                  ranges = secondaryYAxisRange!!,
                )
              }
            }
          }
          // Clear loading state after successful update
          super.handleIntent(GraphIntent.UpdateIsLoading(false))
        }
      } catch (e: Exception) {
        // Log error but don't crash the UI
        android.util.Log.e("GraphViewModel", "Error setting up chart model producer", e)
        // Clear loading state on error
        withContext(Dispatchers.Main) {
          super.handleIntent(GraphIntent.UpdateIsLoading(false))
        }
      }
    }
  }

  private fun calculateYAxisRange(
    graphLines: GraphLine,
    goal: Goal? = null,
    min: Long,
    max: Long,
    isSecondary: Boolean = false,
    isWeightlessMode: Boolean = false,
  ): CartesianRangeValues {
    // Get the end and start timestamp
    val graphLines = graphLines.copy(
      points = graphLines.points.sortedBy { it.x.value.toLong() },
    )
    val initialTimestamp = graphLines.points.minOfOrNull { it.x.value.toLong() }
    val visibleGraphLines = filterXValuesInRange(
      listOf(graphLines),
      min,
      max,
    )

    val paddedValues: List<Double> =
      listOfNotNull(GraphUtil.getPreviousAvailablePoint(graphLines, min, isSecondary)?.toDouble()) +
        visibleGraphLines.flatMap { graphLine -> graphLine.points.map { it.y.value.toDouble() } } +
        listOfNotNull(GraphUtil.getImmediateAvailablePoint(graphLines, max, isSecondary)?.toDouble())

    if (paddedValues.isNotEmpty()) {
      val graphMeta = generateNiceScale(
        minValue = floor(paddedValues.min()),
        maxValue = ceil(paddedValues.max()),
        goalWeight = goal?.goalWeight ?: 0.0,
        isWeightLessMode = isWeightlessMode,
        targetTickCount = 5,
      )
      if (!isSecondary) {
        super.handleIntent(GraphIntent.UpdatePrimaryYStep(graphMeta.step))
      }
      return CartesianRangeValues(
        minY = graphMeta.min,
        maxY = graphMeta.max,
        maxX = if (segment == GraphSegment.TOTAL) max.toDouble() else GraphUtil.getEndRange(
          segment,
          Calendar.getInstance().timeInMillis,
        )?.toDouble(),
        minX = if (segment == GraphSegment.TOTAL) min.toDouble() else GraphUtil.getStartRange(segment, initialTimestamp)
          ?.toDouble(),
      )
    }
    return CartesianRangeValues(
      maxX = if (segment == GraphSegment.TOTAL) max.toDouble() else GraphUtil.getEndRange(
        segment,
        Calendar.getInstance().timeInMillis,
      )?.toDouble(),
      minX = if (segment == GraphSegment.TOTAL) min.toDouble() else GraphUtil.getStartRange(segment, initialTimestamp)
        ?.toDouble(),
    )
  }

  /**
   * Handles scroll events and updates the visible range.
   * Optimized with debouncing and background processing.
   */
  private fun handleScroll(min: Long, max: Long, fallback: () -> Unit = {}) {

    val currentState = _state.value
    // Cancel any existing debounce job
    scrollDebounceJob?.cancel()
    // Debounce heavy computations
    scrollDebounceJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        // Get weightless mode for Y-axis calculations
        val isWeightlessMode = accountService.activeAccountFlow.first()?.isWeightlessOn == true

        val filteredData = currentState.data.filter {
          it.getTimeStamp() in min..max
        }
        if (filteredData.isEmpty()) {
          fallback()
        } else {
          super.handleIntent(GraphIntent.UpdateTarget(filteredData))
        }
        if (isActive) {
          // Pre-calculate all data on background thread
          val graphLines = filterXValuesInRange(currentState.graphLines, min, max)
          graphLines.flatMap { it.points.map { it.x.value.toDouble() } }
          val primaryYAxis = calculateYAxisRange(
            currentState.graphLines.first(),
            goal = currentState.goal,
            min = min,
            max = max,
            isWeightlessMode = isWeightlessMode,
          )
          val secondaryYAxis = if (currentState.secondaryGraphLines != null) {
            calculateYAxisRange(
              currentState.secondaryGraphLines,
              min = min,
              max = max,
              isSecondary = true,
              isWeightlessMode = isWeightlessMode,
            )
          } else null

          // Update UI on main thread
          super.handleIntent(GraphIntent.UpdatePrimaryYAxis(yRangeValues = primaryYAxis))
          if (secondaryYAxis != null) {
            super.handleIntent(GraphIntent.UpdateSecondaryYAxis(yRangeValues = secondaryYAxis))
          }
        }
      } catch (e: Exception) {
        android.util.Log.e("GraphViewModel", "Error handling scroll", e)
      }
    }
  }

  override fun provideInitialState(): GraphState = GraphState()

  override fun onCleared() {
    super.onCleared()
    // Cancel any running jobs
    currentModelProducerJob?.cancel()
    scrollDebounceJob?.cancel()
  }
}
