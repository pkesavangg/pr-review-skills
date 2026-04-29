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
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
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
    super.handleIntent(GraphIntent.UpdateIsSingleWindow(true))
    if (startx != null && endx != null) {
      super.handleIntent(GraphIntent.SetScrollRange(startx, endx))
    }
    super.handleIntent(GraphIntent.UpdateMarkerIndex(null))
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
      val start: Long = _state.value.minTarget
        ?: GraphUtil.getRollingWindowStart(segment, endTimeStamp)
        ?: GraphUtil.getStartRange(segment, endTimeStamp)
        ?: calendar.timeInMillis

      val end: Long = _state.value.maxTarget ?: endTimeStamp ?: calendar.timeInMillis

      start to end
    }

    // `isSingleWindow` represents whether the chart's NATURAL segment window contains all the
    // data — i.e. whether scrolling is meaningful at all. Compute it from the fresh segment
    // window (rolling-window-start..endTimeStamp), NOT from `(startX, endX)` above which may
    // reuse cached `state.minTarget/maxTarget`. Reusing the cached scroll window can trigger
    // a false-positive single-window flag on metric switch (when prior scroll math collapsed
    // the cached pair), which then disables scrolling and snaps the chart to initial.
    val isSingleWindowStart =
      GraphUtil.getRollingWindowStart(segment, endTimeStamp)
        ?: GraphUtil.getStartRange(segment, endTimeStamp)
        ?: calendar.timeInMillis
    val isSingleWindowEnd = endTimeStamp ?: calendar.timeInMillis
    val isSingleWindow = GraphUtil.isSingleWindow(segment, isSingleWindowStart, isSingleWindowEnd)
    super.handleIntent(GraphIntent.UpdateIsSingleWindow(isSingleWindow))


    handleIntent(GraphIntent.UpdateIsEmptyGraph(isEmptyGraph = false))
    super.handleIntent(GraphIntent.SetScrollRange(startX, endX))

    // Synchronous seed for ScrollAwareRangeProvider — uses the SAME bracketing window the
    // provider's `onVisibleEntries` callback uses (visible + 1 entry each side, matching
    // `paddingEntries = 1`). Frame-0 seed and frame-1 callback produce identical Y bounds, so
    // the chart no longer snaps when transitioning from seed to live range. Mirrors MA-3287.
    val isWeightlessSync = accountService.activeAccount.value?.isWeightlessOn == true
    val seedYValues = computeSeedVisibleYWithBracketing(graphLines, startX, endX)
    if (seedYValues.isNotEmpty()) {
      val nice = generateNiceScale(
        minValue = seedYValues.min(),
        maxValue = seedYValues.max(),
        goalWeight = goal?.goalWeight ?: 0.0,
        isWeightLessMode = isWeightlessSync,
        targetTickCount = 4,
      )
      super.handleIntent(GraphIntent.UpdateSeedYRange(seedMinY = nice.min, seedMaxY = nice.max))
    }
    // Skip target updates when a marker is selected — the selected entry should stay reflected
    // in the header even as data re-emits; otherwise the header flashes back to visible-range avg.
    if (currentState.markerIndex == null) {
      val filteredData = data.filter {
        it.getTimeStamp() in startX..endX
      }
      if (filteredData.isNotEmpty())
        super.handleIntent(GraphIntent.UpdateTarget(filteredData))
    }

    currentModelProducerJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        // Get weightless mode before entering transaction
        val isWeightlessMode = accountService.activeAccountFlow.first()?.isWeightlessOn == true

        // Pre-calculate Y-axis range for weight (primary) only
        val axisMeta =
          calculateYAxisRange(graphLines, goal, isWeightlessMode = isWeightlessMode, min = startX, max = endX)
        val primaryYAxisRange = axisMeta.axisRange
        super.handleIntent(GraphIntent.UpdatePrimaryYAxis(primaryYAxisRange, axisMeta.axisStep))

        // Secondary metric flows RAW into the model producer; vico's secondary-layer yTransform
        // (in GraphChart.normalizeSecondaryEntriesToWeightRange) projects it into primary's
        // animation-target yRange on series/target change, then renders against the live yRange.
        // No VM-side renormalization round-trip needed.

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
              )
            }
            // Secondary metric — RAW values; yTransform projects at draw time.
            if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
              val secondaryDataPairs = secondaryGraphLines.points.mapNotNull { point ->
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
  }

  /**
   * Returns the finite Y values within `[startX, endX]` plus one bracketing entry on each
   * side, sorted by X. Mirrors `ScrollAwareRangeProvider.computeVisibleEntries`'s window with
   * `paddingEntries = 1`, so the seed range computed from these values exactly matches the
   * range the provider's callback will produce on the first scroll emission.
   *
   * Uses `binarySearchInsertionPoint` semantics on both edges (matching the provider): for
   * `endX` strictly between two data points, the right-bracketing entry is one position
   * past the largest entry whose X is `<= endX`. The previous `indexOfFirst/Last in range`
   * approach was off-by-one on the right edge whenever `endX` did not coincide with a data
   * point — e.g. after scroll-driven `state.maxTarget` updates — which defeated the seed/live
   * convergence guarantee.
   */
  private fun computeSeedVisibleYWithBracketing(
    graphLines: GraphLine,
    startX: Long,
    endX: Long,
  ): List<Double> {
    val sorted = graphLines.points.sortedBy { it.x.value.toLong() }
    if (sorted.isEmpty()) return emptyList()
    val xs = LongArray(sorted.size) { sorted[it].x.value.toLong() }
    // Insertion point: smallest index where xs[i] >= value (binary search, O(log n)).
    fun insertionPoint(value: Long): Int {
      var low = 0
      var high = xs.size
      while (low < high) {
        val mid = (low + high) ushr 1
        if (xs[mid] < value) low = mid + 1 else high = mid
      }
      return low
    }
    val startIndex = insertionPoint(startX).coerceIn(0, sorted.lastIndex)
    val endIndex = insertionPoint(endX).coerceIn(0, sorted.lastIndex)
    val paddedStart = (startIndex - 1).coerceAtLeast(0)
    val paddedEnd = (endIndex + 1).coerceAtMost(sorted.lastIndex)
    if (paddedStart > paddedEnd) return emptyList()
    return sorted
      .subList(paddedStart, paddedEnd + 1)
      .mapNotNull { (it.y.value as? Number)?.toDouble()?.takeIf { v -> v.isFinite() } }
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
    ) else {
      // No data in visible range — use previous Y axis to avoid blank axis
      // when scrolling beyond data (especially for accounts without a goal).
      val prevYAxis = _state.value.primaryYAxis
      if (prevYAxis?.minY != null && prevYAxis.maxY != null) {
        return AxisMeta(axisRange = prevYAxis, axisStep = _state.value.primaryYStep)
      }
      generateNiceScale(
        minValue = goalWeight.div(10) - 10,
        maxValue = goalWeight.div(10) + 10,
        goalWeight = goalWeight,
        isWeightLessMode = isWeightlessMode,
        targetTickCount = 3,
      )
    }

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
        accountService.activeAccountFlow.first()?.isWeightlessOn == true

        // Skip target updates when a marker is selected — the marker callback owns the target
        // and overwriting it here causes the header to flash back to the visible average.
        if (currentState.markerIndex == null) {
          val filteredData = currentState.data.filter {
            it.getTimeStamp() in min..max
          }
          if (filteredData.isEmpty()) {
            fallback()
          } else {
            super.handleIntent(GraphIntent.UpdateTarget(filteredData))
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLog.e(TAG, "Error handling scroll", e)
      }
    }
  }

  override fun provideInitialState(): GraphState = GraphState()

  override fun onCleared() {
    super.onCleared()
    currentModelProducerJob?.cancel()
    scrollDebounceJob?.cancel()
  }
}
