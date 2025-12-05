package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toGoal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import android.icu.util.Calendar
import android.util.Log

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
      is GraphIntent.UpdateCachedPrimaryYAxis -> {
        // iOS-style: Trigger renormalization when cached Y-axis changes
        // Only renormalize if Y-axis actually changed (avoid unnecessary work)
        val currentCached = _state.value.cachedPrimaryYAxis
        val hasChanged = currentCached == null ||
                        currentCached.minY != intent.yRangeValues.minY ||
                        currentCached.maxY != intent.yRangeValues.maxY

        if (hasChanged) {
          // Use the new Y-axis from intent, not from state (state not updated yet)
          handleRenormalizationOnYAxisChange(intent.yRangeValues)
        }
      }
      is GraphIntent.UpdateWeightUnit -> {
        // Reprocess goal with new unit when weight unit changes
        val currentGoal = _state.value.goal
        val currentAccount = accountService.activeAccount.value
        if (currentGoal != null && currentAccount != null) {
          // Get the raw goal from account (in LB format) and reprocess with new unit
          val rawGoal = currentAccount.toGoal()
          val processedGoal = rawGoal?.process(intent.weightUnit, currentAccount.toWeightless())
          if (processedGoal != null) {
            super.handleIntent(GraphIntent.UpdateGoal(processedGoal))
            // Reinitialize graph with processed goal to update display
            val currentData = _state.value.data
            val currentSecondaryKey = _state.value.secondaryKey
            initializeGraph(currentData, processedGoal, currentSecondaryKey)
          }
        }
      }
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
  private var renormalizationJob: Job? = null

  init {
    initializeWeightUnit()
    initializeImmediateData()
    observeDataChanges()
    subscribeWeightUnit()
  }

  private fun initInialize() {
  }

  /**
   * Initializes the graph with immediate data from services without suspension.
   * This provides instant initialization while the async flows are being set up.
   */
  private fun initializeImmediateData() {
    try {
      // Get immediate data from services (excluding EntryService and AccountService as requested)
      val immediateData = dataFlow.value
      val currentAccount = accountService.activeAccount.value
      val rawGoal = currentAccount?.toGoal()
      // Process goal with current unit and weightless mode to ensure correct unit conversion
      val immediateGoal = rawGoal?.let { goal ->
        val weightUnit = currentAccount?.weightUnit
        val weightless = currentAccount?.toWeightless()
        goal.process(weightUnit, weightless)
      }
      val immediateSecondaryKey = dashboardService.getCurrentSelectedKey()
      super.handleIntent(GraphIntent.UpdateData(immediateData))
      super.handleIntent(GraphIntent.UpdateGoal(immediateGoal))
      super.handleIntent(GraphIntent.SetSecondaryKey(immediateSecondaryKey))
      initializeGraph(immediateData, immediateGoal, immediateSecondaryKey)
    } catch (e: Exception) {
      // Log error but don't crash - fallback to async initialization
      Log.w("GraphViewModel", "Failed to initialize immediate data, falling back to async", e)
    }
  }

  private fun subscribeWeightUnit() {
    viewModelScope.launch {
      accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().drop(1).collect { weightUnit ->
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
    try {
      val currentAccount = accountService.activeAccount.value
      val weightUnit = currentAccount?.weightUnit
      if (weightUnit != null) {
        handleIntent(GraphIntent.UpdateWeightUnit(weightUnit))
      }
    } catch (e: Exception) {
      // Log error but don't crash - fallback to default KG
      Log.w("GraphViewModel", "Failed to initialize weight unit, using default KG", e)
    }
  }

  private fun observeDataChanges() {
    viewModelScope.launch {
      // Start observing combined updates immediately
      // The immediate data initialization already handled the initial state
      combine(
        dataFlow,
        dashboardService.selectedKey,
        goalService.getCurrentGoal(),
      ) { data, secondaryKey, goal ->
        Triple(data, secondaryKey, goal)
      }
        .drop(1)
        .collect { (data, secondaryKey, goal) ->
          handleIntent(GraphIntent.UpdateData(data))
          handleIntent(GraphIntent.UpdateGoal(goal))
          handleIntent(GraphIntent.SetSecondaryKey(secondaryKey))
          initializeGraph(data, goal, secondaryKey = secondaryKey)
        }
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
          val startx = GraphUtil.getStartRange(segment, Calendar.getInstance().timeInMillis)
          val endx = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)
          if (startx != null && endx != null) {
            super.handleIntent(GraphIntent.SetScrollRange(startx, endx))
          }
          super.handleIntent(GraphIntent.UpdateTarget(emptyList()))
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
        Log.e("GraphViewModel", "Error setting up empty chart model producer", e)
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


    currentModelProducerJob = viewModelScope.launch(Dispatchers.IO) {
      try {

        // Get weightless mode before entering transaction
        val isWeightlessMode = accountService.activeAccountFlow.first()?.isWeightlessOn == true

        // Pre-calculate Y-axis range for weight (primary) only
        // Secondary metrics will be normalized to this range (iOS-style)
        // iOS-style: Use cached Y-axis if available, otherwise calculate fresh
        val primaryYAxisRange = calculateYAxisRange(graphLines, goal, isWeightlessMode = isWeightlessMode, min = startX, max = endX)

        // Cache the Y-axis for future scroll updates (iOS-style)
        if (currentState.cachedPrimaryYAxis == null) {
          withContext(Dispatchers.Main) {
            super.handleIntent(GraphIntent.UpdateCachedPrimaryYAxis(yRangeValues = primaryYAxisRange))
          }
        }

        // Normalize secondary metric values to weight Y-axis range (iOS-style normalization)
        // Validate Y-axis range values are finite before normalization (matching iOS defensive checks)
        val normalizedSecondaryGraphLines = if (secondaryGraphLines != null &&
                                                   secondaryGraphLines.points.isNotEmpty() &&
                                                   primaryYAxisRange.minY != null &&
                                                   primaryYAxisRange.maxY != null &&
                                                   primaryYAxisRange.minY!!.isFinite() &&
                                                   primaryYAxisRange.maxY!!.isFinite() &&
                                                   primaryYAxisRange.minY!! < primaryYAxisRange.maxY!!) {
          // Extract metric key from secondaryKey for metric-specific static ranges (iOS-style)
          val metricKey = (secondaryStat as? DashboardKey.Metric)?.key
          GraphUtil.normalizeMetricToWeightRange(
            metricGraphLine = secondaryGraphLines,
            weightMin = primaryYAxisRange.minY!!,
            weightMax = primaryYAxisRange.maxY!!,
            minX = startX,
            maxX = endX,
            metricKey = metricKey
          )
        } else null


        // Pre-calculate series data on background thread
        // Filter out NaN/Infinity values and invalid X/Y pairs (matching iOS defensive checks)
        val primaryYDataPairs = xLabels.zip(ySeries).mapNotNull { (xLabel, yLabel) ->
          val xValue = xLabel.value as? Long
          val yValue = yLabel.value as? Double
          if (xValue != null && yValue != null && yValue.isFinite()) {
            Pair(xValue, yValue)
          } else null
        }
        val primaryXDataFiltered = primaryYDataPairs.map { it.first }
        val primaryYDataFiltered = primaryYDataPairs.map { it.second }

        // Validate Y-axis range is finite before using
        if (primaryYAxisRange.minY == null || primaryYAxisRange.maxY == null ||
            !primaryYAxisRange.minY!!.isFinite() || !primaryYAxisRange.maxY!!.isFinite()) {
          Log.e("GraphViewModel", "Invalid primary Y-axis range: ${primaryYAxisRange.minY} - ${primaryYAxisRange.maxY}")
          withContext(Dispatchers.Main) {
            super.handleIntent(GraphIntent.UpdateIsLoading(false))
          }
          return@launch
        }

        // Check if job is still active before running transaction
        if (isActive && primaryXDataFiltered.isNotEmpty() && primaryYDataFiltered.isNotEmpty() &&
            primaryXDataFiltered.size == primaryYDataFiltered.size) {
          // Switch to main thread for UI updates
          currentState.modelProducer.runTransaction {
            lineSeries {
              series(
                x = primaryXDataFiltered,
                y = primaryYDataFiltered,
                ranges = primaryYAxisRange,
              )
            }
            // Secondary metrics now use the same Y-axis range as primary (normalized values)
            if (normalizedSecondaryGraphLines != null && normalizedSecondaryGraphLines.points.isNotEmpty()) {
              // Filter out NaN/Infinity values from secondary Y data (matching iOS defensive checks)
              val secondaryDataPairs = normalizedSecondaryGraphLines.points.mapNotNull { point ->
                val xValue = point.x.value as? Long
                val yValue = (point.y.value as? Number)?.toDouble()
                if (xValue != null && yValue != null && yValue.isFinite()) {
                  Pair(xValue, yValue)
                } else null
              }
              val secondaryXDataFiltered = secondaryDataPairs.map { it.first }
              val secondaryYDataFiltered = secondaryDataPairs.map { it.second }

              if (secondaryXDataFiltered.isNotEmpty() && secondaryYDataFiltered.isNotEmpty() &&
                  secondaryXDataFiltered.size == secondaryYDataFiltered.size) {
                lineSeries {
                  series(
                    x = secondaryXDataFiltered,
                    y = secondaryYDataFiltered,
                    ranges = primaryYAxisRange, // Use same range as primary (weight)
                  )
                }
              }
            }
          }
          // Clear loading state after successful update
          super.handleIntent(GraphIntent.UpdateIsLoading(false))
        }
      } catch (e: Exception) {
        // Log error but don't crash the UI
        Log.e("GraphViewModel", "Error setting up chart model producer", e)
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

    // Filter out NaN and infinite values before calculating min/max
    val validPaddedValues = paddedValues.filter { it.isFinite() }

    if (validPaddedValues.isNotEmpty()) {
      val minValue = floor(validPaddedValues.min())
      val maxValue = ceil(validPaddedValues.max())

      val graphMeta = generateNiceScale(
        minValue = minValue,
        maxValue = maxValue,
        goalWeight = goal?.goalWeight ?: 0.0, isWeightLessMode = isWeightlessMode,
        targetTickCount = 5,
      )

      // Validate graphMeta values are finite
      if (!graphMeta.min.isFinite() || !graphMeta.max.isFinite()) {
        // Return default range if graphMeta is invalid
        return CartesianRangeValues(
          maxX = if (segment == GraphSegment.TOTAL) max.toDouble() else GraphUtil.getEndRange(
            segment,
            Calendar.getInstance().timeInMillis,
          )?.toDouble(),
          minX = if (segment == GraphSegment.TOTAL) min.toDouble() else GraphUtil.getStartRange(segment, initialTimestamp)
            ?.toDouble(),
        )
      }

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
   * iOS-style: Caches Y-axis on scroll end to trigger renormalization.
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


          // iOS-style: Cache Y-axis on scroll end to enable renormalization
          // This triggers renormalization when Y-axis domain changes
          withContext(Dispatchers.Main) {
            super.handleIntent(GraphIntent.UpdateCachedPrimaryYAxis(yRangeValues = primaryYAxis))
            // Directly call renormalization with current scroll range to avoid reading stale state values
            handleRenormalizationOnYAxisChange(newYAxisRange = primaryYAxis, min = min, max = max)
          }


          // Update UI on main thread
          super.handleIntent(GraphIntent.UpdatePrimaryYAxis(yRangeValues = primaryYAxis))
        }
      } catch (e: Exception) {
        Log.e("GraphViewModel", "Error handling scroll", e)
      }
    }
  }

  override fun provideInitialState(): GraphState = GraphState()

  /**
   * Handles renormalization when cached Y-axis domain changes (iOS-style).
   * Regenerates normalized secondary metric series with new Y-axis domain.
   *
   * @param newYAxisRange The new Y-axis range to use for normalization.
   *                      If null, reads from current state.
   * @param min Optional minimum X-axis range. If null, reads from state.
   * @param max Optional maximum X-axis range. If null, reads from state.
   */
  private fun handleRenormalizationOnYAxisChange(
    newYAxisRange: CartesianRangeValues? = null,
    min: Long? = null,
    max: Long? = null
  ) {
    val currentState = _state.value
    val secondaryKey = currentState.secondaryKey
    val data = currentState.data

    // Use provided Y-axis or fallback to cached from state
    val cachedYAxis = newYAxisRange ?: currentState.cachedPrimaryYAxis

    // Only renormalize if we have secondary metrics and Y-axis
    // Validate Y-axis values are finite before use (matching iOS defensive checks)
    if (secondaryKey == null || data.isEmpty() || cachedYAxis == null ||
        cachedYAxis.minY == null || cachedYAxis.maxY == null ||
        !cachedYAxis.minY!!.isFinite() || !cachedYAxis.maxY!!.isFinite() ||
        cachedYAxis.minY!! >= cachedYAxis.maxY!!) {
      return
    }

    // Cancel any existing renormalization job
    renormalizationJob?.cancel()

    renormalizationJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val graphLines = data.getWeightGraphPoints()
        val secondaryGraphLines = data.toGraphPoints((secondaryKey as DashboardKey.Metric).key)

        // Use provided min/max or fallback to state values
        val startX = min ?: currentState.minTarget ?: graphLines.points.minOfOrNull { it.x.value.toLong() }
          ?: Calendar.getInstance().timeInMillis
        val endX = max ?: currentState.maxTarget ?: graphLines.points.maxOfOrNull { it.x.value.toLong() }
          ?: Calendar.getInstance().timeInMillis

        // Renormalize secondary metrics with cached Y-axis (iOS-style)
        // Y-axis values already validated above, safe to use here
        val normalizedSecondaryGraphLines = if (secondaryGraphLines.points.isNotEmpty()) {
          val originalValues = secondaryGraphLines.points.take(3).map { (it.y.value as? Number)?.toDouble() }

          // Extract metric key from secondaryKey for metric-specific static ranges (iOS-style)
          val metricKey = (secondaryKey as? DashboardKey.Metric)?.key
          val normalized = GraphUtil.normalizeMetricToWeightRange(
            metricGraphLine = secondaryGraphLines,
            weightMin = cachedYAxis.minY!!,
            weightMax = cachedYAxis.maxY!!,
            minX = startX,
            maxX = endX,
            metricKey = metricKey
          )

          val normalizedValues = normalized.points.take(3).map { (it.y.value as? Number)?.toDouble() }
          normalized
        } else null

        // Update chart model producer with renormalized values
        if (isActive && normalizedSecondaryGraphLines != null && normalizedSecondaryGraphLines.points.isNotEmpty()) {
          // Filter out NaN/Infinity values from primary Y data (matching iOS defensive checks)
          val primaryDataPairs = graphLines.points.mapNotNull { point ->
            val xValue = point.x.value as? Long
            val yValue = point.y.value as? Double
            if (xValue != null && yValue != null && yValue.isFinite()) {
              Pair(xValue, yValue)
            } else null
          }
          val primaryXData = primaryDataPairs.map { it.first }
          val primaryYData = primaryDataPairs.map { it.second }

          // Filter out NaN/Infinity values from secondary Y data (matching iOS defensive checks)
          val secondaryDataPairs = normalizedSecondaryGraphLines.points.mapNotNull { point ->
            val xValue = point.x.value as? Long
            val yValue = (point.y.value as? Number)?.toDouble()
            if (xValue != null && yValue != null && yValue.isFinite()) {
              Pair(xValue, yValue)
            } else null
          }
          val secondaryXData = secondaryDataPairs.map { it.first }
          val secondaryYData = secondaryDataPairs.map { it.second }

          // Only update chart if we have valid data pairs
          if (primaryXData.isNotEmpty() && primaryYData.isNotEmpty() &&
              primaryXData.size == primaryYData.size) {
            currentState.modelProducer.runTransaction {
              // Update primary series
              lineSeries {
                series(
                  x = primaryXData,
                  y = primaryYData,
                  ranges = cachedYAxis,
                )
              }
              // Update secondary series with renormalized values (only if valid)
              if (secondaryXData.isNotEmpty() && secondaryYData.isNotEmpty() &&
                  secondaryXData.size == secondaryYData.size) {
                lineSeries {
                  series(
                    x = secondaryXData,
                    y = secondaryYData,
                    ranges = cachedYAxis, // Use cached Y-axis (same as primary)
                  )
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        Log.e("GraphViewModel", "Error renormalizing on Y-axis change", e)
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    // Cancel any running jobs
    currentModelProducerJob?.cancel()
    scrollDebounceJob?.cancel()
    renormalizationJob?.cancel()
  }
}
