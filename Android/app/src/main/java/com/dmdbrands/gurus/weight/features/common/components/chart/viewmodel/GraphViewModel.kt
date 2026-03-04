package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toGoal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.filterXValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.chart.AxisMeta
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
  @Assisted val anchoredScrollTarget: Double?,
  private val dashboardService: IDashboardService,
  private val goalService: IGoalService,
  private val entryService: IEntryService,
  private val accountService: IAccountService
) : BaseIntentViewModel<GraphState, GraphIntent>(
  reducer = GraphReducer(),
) {

  companion object {
    private const val TAG = "GraphViewModel"
  }

  override fun handleIntent(intent: GraphIntent) {
    super.handleIntent(intent)
    when (intent) {
      is GraphIntent.SetScrollRange -> handleScroll(intent.min, intent.max, intent.onFallback)
      else -> null
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(segment: GraphSegment, anchoredScrollTarget: Double?): GraphViewModel
  }

  private val dataFlow = if (segment == GraphSegment.WEEK || segment == GraphSegment.MONTH) {
    entryService.daywiseBodyScaleAverages
  } else {
    entryService.monthlyBodyScaleAverages
  }

  private var currentModelProducerJob: Job? = null
  private var scrollDebounceJob: Job? = null
  private var renormalizationJob: Job? = null
  private var isInitialized: Boolean = false

  init {
    // Set loading state immediately to prevent blank screen
    initializeWeightUnit()
    initializeImmediateData()
    observeDataChanges()
    subscribeWeightUnit()
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
        val weightUnit = currentAccount.weightUnit
        val weightless = currentAccount.toWeightless()
        goal.process(weightUnit, weightless)
      }
      val immediateSecondaryKey = dashboardService.getCurrentSelectedKey()
      super.handleIntent(GraphIntent.UpdateData(immediateData))
      super.handleIntent(GraphIntent.UpdateGoal(immediateGoal))
      super.handleIntent(GraphIntent.SetSecondaryKey(immediateSecondaryKey))
      initializeGraph(immediateData, immediateGoal, immediateSecondaryKey)
    } catch (e: Exception) {
      AppLog.w(TAG, "Failed to initialize immediate data, falling back to async")
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
      AppLog.w(TAG, "Failed to initialize weight unit, using default KG")
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
        .collect { (data, secondaryKey) ->
          val currentAccount = accountService.activeAccount.value
          val changedGoal = currentAccount?.toGoal()
          // Process goal with current unit and weightless mode to ensure correct unit conversion
          val goal = changedGoal?.let { goal ->
            val weightUnit = currentAccount.weightUnit
            val weightless = currentAccount.toWeightless()
            goal.process(weightUnit, weightless)
          }
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
    if (data.isNotEmpty()) {
      setupChartModelProducer(data, secondaryKey, goal)
    } else {
      setupEmptyModelProducer(goal)
    }
  }

  /**
   * Sets up an empty chart model producer when no data is available.
   * Optimized to set empty state immediately and update model producer asynchronously.
   */
  private fun setupEmptyModelProducer(goal: Goal?) {
    val currentState = state.value
    // Cancel any existing model producer job
    currentModelProducerJob?.cancel()

    // Set empty state immediately to avoid blank screen
    handleIntent(GraphIntent.UpdateIsEmptyGraph(isEmptyGraph = true))
    val startx = GraphUtil.getStartRange(segment, Calendar.getInstance().timeInMillis)
    val endx = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)
    val isSingleWindow = GraphUtil.isSingleWindow(segment, startx, endx)
    super.handleIntent(GraphIntent.UpdateIsSingleWindow(isSingleWindow))
    if (startx != null && endx != null) {
      super.handleIntent(GraphIntent.SetScrollRange(startx, endx))
    }
    super.handleIntent(GraphIntent.UpdateTarget(emptyList()))

    // Set empty model producer asynchronously
    currentModelProducerJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        // Check if job is still active before running transaction
        if (isActive) {
          val isWeightlessMode = accountService.activeAccountFlow.first()?.isWeightlessOn == true

          val graphMeta = if (goal != null) generateNiceScale(
            goal.goalWeight.div(10.0) - 10.0,
            goal.goalWeight.div(10.0) + 10.0,
            goal.goalWeight.div(10.0),
            isWeightLessMode = isWeightlessMode,
            targetTickCount = 4,
          ) else null

          if (graphMeta != null) {
            handleIntent(GraphIntent.UpdatePrimaryYStep(graphMeta.step))
          }
          withContext(Dispatchers.Main) {
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
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error setting up empty chart model producer", e)
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
    val isSingleWindow = GraphUtil.isSingleWindow(segment, initialTimeStamp, endTimeStamp)
    super.handleIntent(GraphIntent.UpdateIsSingleWindow(isSingleWindow))
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
      val anchoredScrollTargetConsideration = anchoredScrollTarget != null && !isInitialized
      val start: Long =
        anchoredScrollTarget?.toLong()
          ?.takeIf { anchoredScrollTargetConsideration }
          ?: _state.value.minTarget ?: GraphUtil.getRollingWindowStart(segment, endTimeStamp)
          ?: GraphUtil.getStartRange(segment, endTimeStamp)
          ?: calendar.timeInMillis

      val end =
        GraphUtil.getRollingWindowEnd(segment, anchoredScrollTarget?.toLong())
          ?.takeIf { anchoredScrollTargetConsideration }
          ?: _state.value.maxTarget ?: endTimeStamp ?: calendar.timeInMillis

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
        val axisMeta =
          calculateYAxisRange(graphLines, goal, isWeightlessMode = isWeightlessMode, min = startX, max = endX)
        val primaryYAxisRange = axisMeta.axisRange
        super.handleIntent(GraphIntent.UpdatePrimaryYAxis(primaryYAxisRange, axisMeta.axisStep))
        val primaryMinY = primaryYAxisRange.minY
        val primaryMaxY = primaryYAxisRange.maxY
        val normalizedSecondaryGraphLines = if (secondaryGraphLines != null &&
          secondaryGraphLines.points.isNotEmpty() &&
          primaryMinY != null &&
          primaryMaxY != null &&
          primaryMinY.isFinite() &&
          primaryMaxY.isFinite() &&
          primaryMinY < primaryMaxY
        ) {
          // Extract metric key from secondaryKey for metric-specific static ranges (iOS-style)
          GraphUtil.normalizeMetricToWeightRange(
            metricGraphLine = secondaryGraphLines,
            weightMin = primaryMinY,
            weightMax = primaryMaxY,
            minX = startX,
            maxX = endX,
          )
        } else null

        // Pre-calculate series data on background thread
        val primaryYDataPairs = xLabels.zip(ySeries).mapNotNull { (xLabel, yLabel) ->
          val xValue = xLabel.value as? Long
          val yValue = yLabel.value as? Double
          if (xValue != null && yValue != null && yValue.isFinite()) {
            Pair(xValue, yValue)
          } else null
        }
        val primaryXDataFiltered = primaryYDataPairs.map { it.first }
        val primaryYDataFiltered = primaryYDataPairs.map { it.second }

        // Check if job is still active before running transaction
        if (isActive && primaryXDataFiltered.isNotEmpty() && primaryYDataFiltered.isNotEmpty() &&
          primaryXDataFiltered.size == primaryYDataFiltered.size
        ) {
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
                secondaryXDataFiltered.size == secondaryYDataFiltered.size
              ) {
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
        AppLog.e(TAG, "Error setting up chart model producer", e)
      }
    }
    this.isInitialized = true
  }

  private fun calculateYAxisRange(
    graphLines: GraphLine,
    goal: Goal? = null,
    min: Long,
    max: Long,
    isSecondary: Boolean = false,
    isWeightlessMode: Boolean = false,
  ): AxisMeta {
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
      listOfNotNull(GraphUtil.getPreviousAvailablePoint(graphLines, min)) +
        visibleGraphLines.flatMap { graphLine -> graphLine.points.map { it.y.value.toDouble() } } +
        listOfNotNull(GraphUtil.getImmediateAvailablePoint(graphLines, max))

    // Filter out NaN and infinite values before calculating min/max
    val validPaddedValues = paddedValues.filter { it.isFinite() }

    val goalWeight = goal?.goalWeight ?: 0.0

    val graphMeta = if (validPaddedValues.isNotEmpty()) generateNiceScale(
      minValue = validPaddedValues.min(),
      maxValue = validPaddedValues.max(),
      goalWeight = goalWeight,
      isWeightLessMode = isWeightlessMode,
      targetTickCount = 4,
    ) else generateNiceScale(
      minValue = goalWeight.div(10) - 10,
      maxValue = goalWeight.div(10) + 10,
      goalWeight = goalWeight,
      isWeightLessMode = isWeightlessMode,
      targetTickCount = 3,
    )

    // Validate graphMeta values are finite
    if (!graphMeta.min.isFinite() || !graphMeta.max.isFinite()) {
      // Return default range if graphMeta is invalid
      return AxisMeta(
        axisRange = CartesianRangeValues(
          maxX = if (segment == GraphSegment.TOTAL) max.toDouble() else GraphUtil.getEndRange(
            segment,
            Calendar.getInstance().timeInMillis,
          )?.toDouble(),
          minX = if (segment == GraphSegment.TOTAL) min.toDouble() else GraphUtil.getStartRange(
            segment,
            initialTimestamp,
          )
            ?.toDouble(),
        ),
      )
    }

    return AxisMeta(
      axisRange = CartesianRangeValues(
        minY = graphMeta.min,
        maxY = graphMeta.max,
        maxX = if (segment == GraphSegment.TOTAL) max.toDouble() else if (segment == GraphSegment.MONTH) {
          val paddedEnd_StartRange = GraphUtil.getStartRange(segment, java.util.Calendar.getInstance().timeInMillis)
            ?: Calendar.getInstance().timeInMillis
          val paddedEndX = Calendar.getInstance().apply {
            timeInMillis = paddedEnd_StartRange
            add(Calendar.DAY_OF_YEAR, 30)
          }.timeInMillis
          paddedEndX.toDouble()
        } else GraphUtil.getEndRange(
          segment,
          Calendar.getInstance().timeInMillis,
        )?.toDouble(),
        minX = if (segment == GraphSegment.TOTAL) min.toDouble() else GraphUtil.getStartRange(
          segment,
          initialTimestamp,
        )
          ?.toDouble(),
      ),
      axisStep = graphMeta.step,
    )
  }

  /**
   * Handles scroll events and updates the visible range.
   * Optimized with debouncing and background processing.
   * iOS-style: Caches Y-axis on scroll end to trigger renormalization.
   */
  private fun handleScroll(min: Long, max: Long, fallback: () -> Unit = {}) {
    val min = GraphUtil.getRelativeStart(segment, min)
    val max = GraphUtil.getRelativeEnd(segment, max)
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
          val primaryYAxis = calculateYAxisRange(
            currentState.graphLines.first(),
            goal = currentState.goal,
            min = min,
            max = max,
            isWeightlessMode = isWeightlessMode,
          )

          // This triggers renormalization when Y-axis domain changes
          withContext(Dispatchers.Main) {
            // Directly call renormalization with current scroll range to avoid reading stale state values
            handleRenormalizationOnYAxisChange(newYAxisRange = primaryYAxis.axisRange, min = min, max = max)
            super.handleIntent(
              GraphIntent.UpdatePrimaryYAxis(
                yRangeValues = primaryYAxis.axisRange,
                yStep = primaryYAxis.axisStep,
              ),
            )
          }

          // Update UI on main thread
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLog.e(TAG, "Error handling scroll", e)
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
    val cachedYAxis = newYAxisRange

    // Only renormalize if we have secondary metrics and Y-axis
    // Validate Y-axis values are finite before use (matching iOS defensive checks)
    val cachedMinY = cachedYAxis?.minY
    val cachedMaxY = cachedYAxis?.maxY
    if (secondaryKey == null || data.isEmpty() || cachedYAxis == null ||
      cachedMinY == null || cachedMaxY == null ||
      !cachedMinY.isFinite() || !cachedMaxY.isFinite() ||
      cachedMinY >= cachedMaxY
    ) {
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
          secondaryGraphLines.points.take(3).map { (it.y.value as? Double?) }

          // Extract metric key from secondaryKey for metric-specific static ranges (iOS-style)
          val normalized = GraphUtil.normalizeMetricToWeightRange(
            metricGraphLine = secondaryGraphLines,
            weightMin = cachedMinY,
            weightMax = cachedMaxY,
            minX = startX,
            maxX = endX,
          )

          normalized.points.take(3).map { (it.y.value as? Double?) }
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
            primaryXData.size == primaryYData.size
          ) {
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
                secondaryXData.size == secondaryYData.size
              ) {
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
        AppLog.e(TAG, "Error renormalizing on Y-axis change", e)
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
