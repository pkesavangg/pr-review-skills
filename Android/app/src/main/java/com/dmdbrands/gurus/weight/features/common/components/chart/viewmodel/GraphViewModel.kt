package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toGoal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.toWeightless
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.components.chart.CartesianRangeValues
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.filterXValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.chart.AxisMeta
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
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
 * ViewModel for the graph component. One instance per segment (4 total).
 * Holds per-product state in [GraphState.productStates] map.
 * Each product's [ProductGraphState.modelProducer] is stable and never recreated.
 * All product flows collect independently in the background.
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
  private val historyService: IHistoryService,
  private val accountService: IAccountService,
) : BaseIntentViewModel<GraphState, GraphIntent>(
  reducer = GraphReducer(),
) {

  companion object {
    private const val TAG = "GraphViewModel"
  }

  override fun handleIntent(intent: GraphIntent) {
    super.handleIntent(intent)
    when (intent) {
      is GraphIntent.SetProductScrollRange -> handleScroll(
        intent.productType, intent.min, intent.max, intent.onFallback,
      )
      else -> null
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(segment: GraphSegment, anchoredScrollTarget: Double?): GraphViewModel
  }

  private val weightDataFlow = if (segment == GraphSegment.WEEK || segment == GraphSegment.MONTH) {
    entryService.daywiseBodyScaleAverages
  } else {
    entryService.monthlyBodyScaleAverages
  }

  private var currentWeightModelJob: Job? = null
  private var scrollDebounceJob: Job? = null
  private var isInitialized: Boolean = false
  private val productDataJobs = mutableMapOf<ProductType, Job>()

  init {
    // Ensure weight product state exists immediately
    super.handleIntent(GraphIntent.AddProductState(ProductType.MY_WEIGHT))
    initializeWeightUnit()
    initializeImmediateData()
    observeWeightDataChanges()
    subscribeWeightUnit()
  }

  override fun onDependenciesReady() {
    observeAvailableProducts()
  }

  // ── Product-aware data collection (lazy) ──

  private fun observeAvailableProducts() {
    viewModelScope.launch {
      productSelectionManager.availableProducts.collectLatest { products ->
        for (product in products) {
          val pt = product.productType
          if (pt == ProductType.MY_WEIGHT) continue // handled by weight-specific flow
          if (productDataJobs.containsKey(pt)) continue
          // Add product state entry (with fresh producer)
          super.handleIntent(GraphIntent.AddProductState(pt))
          startProductDataCollection(product)
        }
      }
    }
  }

  private fun startProductDataCollection(product: ProductSelection) {
    val pt = product.productType
    val adapter = GraphDataAdapter.forProduct(product)
    val graphDataFlow: Flow<GraphData> = if (segment == GraphSegment.WEEK || segment == GraphSegment.MONTH) {
      historyService.getDailyGraphData(product)
    } else {
      historyService.getMonthlyGraphData(product)
    }
    productDataJobs[pt] = viewModelScope.launch {
      graphDataFlow.collect { graphData ->
        pushProductData(pt, adapter, graphData)
      }
    }
  }

  private suspend fun pushProductData(
    productType: ProductType,
    adapter: GraphDataAdapter,
    graphData: GraphData,
  ) {
    val seriesList = adapter.toLineSeries(graphData)
    val producer = _state.value.forProduct(productType).modelProducer

    if (seriesList.isEmpty() || seriesList.all { it.xValues.isEmpty() }) {
      super.handleIntent(GraphIntent.UpdateProductIsEmptyGraph(productType, true))
      withContext(Dispatchers.Main) {
        producer.runTransaction(animate = false) {
          lineSeries { series(listOf(0.0), listOf(0.0)) }
        }
      }
      return
    }

    // Compute ranges from adapter data
    val timestamps = adapter.getTimestamps(graphData).sorted()
    val initialTimeStamp = timestamps.minOrNull()
    val endTimeStamp = timestamps.maxOrNull()
    val isSingleWindow = GraphUtil.isSingleWindow(segment, initialTimeStamp, endTimeStamp)
    super.handleIntent(GraphIntent.UpdateProductIsSingleWindow(productType, isSingleWindow))
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
      val start = GraphUtil.getRollingWindowStart(segment, endTimeStamp)
        ?: GraphUtil.getStartRange(segment, endTimeStamp)
        ?: calendar.timeInMillis
      val end = endTimeStamp ?: calendar.timeInMillis
      start to end
    }

    super.handleIntent(GraphIntent.UpdateProductIsEmptyGraph(productType, false))
    super.handleIntent(GraphIntent.SetProductScrollRange(productType, startX, endX))

    val targetData = adapter.toTargetData(graphData)
    super.handleIntent(GraphIntent.UpdateProductData(productType, targetData))

    val chartMinX = if (segment == GraphSegment.TOTAL) {
      startX.toDouble()
    } else {
      GraphUtil.getStartRange(segment, initialTimeStamp)?.toDouble() ?: startX.toDouble()
    }
    val chartMaxX = if (segment == GraphSegment.TOTAL) {
      endX.toDouble()
    } else if (segment == GraphSegment.MONTH) {
      val paddedStart = GraphUtil.getStartRange(segment, calendar.timeInMillis) ?: calendar.timeInMillis
      Calendar.getInstance().apply { timeInMillis = paddedStart; add(Calendar.DAY_OF_YEAR, 30) }
        .timeInMillis.toDouble()
    } else {
      GraphUtil.getEndRange(segment, calendar.timeInMillis)?.toDouble() ?: endX.toDouble()
    }
    super.handleIntent(GraphIntent.UpdateProductChartXRange(productType, chartMinX, chartMaxX))

    val filteredTarget = targetData.filter { it.getTimeStamp() in startX..endX }
    if (filteredTarget.isNotEmpty()) {
      super.handleIntent(GraphIntent.UpdateProductTarget(productType, filteredTarget))
    }

    withContext(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        lineSeries {
          seriesList.forEach { s -> series(x = s.xValues, y = s.yValues) }
        }
      }
    }
  }

  // ── Weight-specific (original logic, uses product-scoped intents) ──

  private fun initializeImmediateData() {
    try {
      val immediateData = weightDataFlow.value
      val currentAccount = accountService.activeAccount.value
      val rawGoal = currentAccount?.toGoal()
      val immediateGoal = rawGoal?.let { goal ->
        val weightUnit = currentAccount.weightUnit
        val weightless = currentAccount.toWeightless()
        goal.process(weightUnit, weightless)
      }
      super.handleIntent(GraphIntent.UpdateProductData(ProductType.MY_WEIGHT, immediateData))
      super.handleIntent(GraphIntent.UpdateGoal(immediateGoal))
      initializeWeightGraph(immediateData, immediateGoal, secondaryKey = null)
    } catch (e: Exception) {
      AppLog.w(TAG, "Failed to initialize immediate data, falling back to async")
    }
  }

  private fun observeWeightDataChanges() {
    viewModelScope.launch {
      combine(
        weightDataFlow,
        dashboardService.selectedKey,
        goalService.getCurrentGoal(),
      ) { data, secondaryKey, goal ->
        Triple(data, secondaryKey, goal)
      }
        .drop(1)
        .collect { (data, secondaryKey) ->
          val currentAccount = accountService.activeAccount.value
          val changedGoal = currentAccount?.toGoal()
          val goal = changedGoal?.let { goal ->
            val weightUnit = currentAccount.weightUnit
            val weightless = currentAccount.toWeightless()
            goal.process(weightUnit, weightless)
          }
          handleIntent(GraphIntent.UpdateProductData(ProductType.MY_WEIGHT, data))
          handleIntent(GraphIntent.UpdateGoal(goal))
          handleIntent(GraphIntent.SetSecondaryKey(secondaryKey))
          initializeWeightGraph(data, goal, secondaryKey = secondaryKey)
        }
    }
  }

  private fun initializeWeightGraph(
    data: List<PeriodBodyScaleSummary>? = null,
    goal: Goal? = null,
    secondaryKey: DashboardKey? = null,
  ) {
    val ps = _state.value.forProduct(ProductType.MY_WEIGHT)
    val data = data ?: ps.data
    val goal = goal ?: _state.value.goal
    val secondaryKey = secondaryKey ?: _state.value.secondaryKey
    scrollDebounceJob?.cancel()
    if (data.isNotEmpty()) {
      setupWeightChartModelProducer(data, secondaryKey, goal)
    } else {
      setupEmptyWeightModelProducer()
    }
  }

  private fun setupEmptyWeightModelProducer() {
    val producer = _state.value.forProduct(ProductType.MY_WEIGHT).modelProducer
    currentWeightModelJob?.cancel()
    handleIntent(GraphIntent.UpdateProductIsEmptyGraph(ProductType.MY_WEIGHT, true))
    val startx = GraphUtil.getStartRange(segment, Calendar.getInstance().timeInMillis)
    val endx = GraphUtil.getEndRange(segment, Calendar.getInstance().timeInMillis)
    val isSingleWindow = GraphUtil.isSingleWindow(segment, startx, endx)
    super.handleIntent(GraphIntent.UpdateProductIsSingleWindow(ProductType.MY_WEIGHT, isSingleWindow))
    if (startx != null && endx != null) {
      super.handleIntent(GraphIntent.SetProductScrollRange(ProductType.MY_WEIGHT, startx, endx))
    }
    super.handleIntent(GraphIntent.UpdateProductTarget(ProductType.MY_WEIGHT, emptyList()))
    currentWeightModelJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        if (isActive) {
          withContext(Dispatchers.Main) {
            producer.runTransaction(animate = false) {
              lineSeries { series(listOf(0.0), listOf(0.0)) }
            }
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error setting up empty chart model producer", e)
      }
    }
  }

  private fun setupWeightChartModelProducer(
    data: List<PeriodBodyScaleSummary>,
    secondaryKey: DashboardKey? = null,
    goal: Goal?,
  ) {
    val producer = _state.value.forProduct(ProductType.MY_WEIGHT).modelProducer
    currentWeightModelJob?.cancel()
    val graphLines = data.getWeightGraphPoints()
    val secondaryStat = secondaryKey ?: _state.value.secondaryKey
    val secondaryGraphLines = secondaryStat?.let { data.toGraphPoints((it as DashboardKey.Metric).key) }
    val xLabels = graphLines.points.map { point -> point.x }
    val ySeries = graphLines.points.map { it.y }
    val initialTimeStamp = graphLines.points.minOfOrNull { it.x.value.toLong() }
    val endTimeStamp = graphLines.points.maxOfOrNull { it.x.value.toLong() }
    val isSingleWindow = GraphUtil.isSingleWindow(segment, initialTimeStamp, endTimeStamp)
    super.handleIntent(GraphIntent.UpdateProductIsSingleWindow(ProductType.MY_WEIGHT, isSingleWindow))
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
      val ps = _state.value.forProduct(ProductType.MY_WEIGHT)
      val start: Long =
        anchoredScrollTarget?.toLong()
          ?.takeIf { anchoredScrollTargetConsideration }
          ?: ps.minTarget ?: GraphUtil.getRollingWindowStart(segment, endTimeStamp)
          ?: GraphUtil.getStartRange(segment, endTimeStamp)
          ?: calendar.timeInMillis
      val end =
        GraphUtil.getRollingWindowEnd(segment, anchoredScrollTarget?.toLong())
          ?.takeIf { anchoredScrollTargetConsideration }
          ?: ps.maxTarget ?: endTimeStamp ?: calendar.timeInMillis
      start to end
    }

    handleIntent(GraphIntent.UpdateProductIsEmptyGraph(ProductType.MY_WEIGHT, false))
    super.handleIntent(GraphIntent.SetProductScrollRange(ProductType.MY_WEIGHT, startX, endX))

    val chartMinX = if (segment == GraphSegment.TOTAL) {
      startX.toDouble()
    } else {
      GraphUtil.getStartRange(segment, initialTimeStamp)?.toDouble() ?: startX.toDouble()
    }
    val chartMaxX = if (segment == GraphSegment.TOTAL) {
      endX.toDouble()
    } else if (segment == GraphSegment.MONTH) {
      val paddedStart = GraphUtil.getStartRange(segment, calendar.timeInMillis) ?: calendar.timeInMillis
      Calendar.getInstance().apply { timeInMillis = paddedStart; add(Calendar.DAY_OF_YEAR, 30) }
        .timeInMillis.toDouble()
    } else {
      GraphUtil.getEndRange(segment, calendar.timeInMillis)?.toDouble() ?: endX.toDouble()
    }
    super.handleIntent(GraphIntent.UpdateProductChartXRange(ProductType.MY_WEIGHT, chartMinX, chartMaxX))
    val filteredData = data.filter { it.getTimeStamp() in startX..endX }
    if (filteredData.isNotEmpty())
      super.handleIntent(GraphIntent.UpdateProductTarget(ProductType.MY_WEIGHT, filteredData))

    currentWeightModelJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val primaryYDataPairs = xLabels.zip(ySeries).mapNotNull { (xLabel, yLabel) ->
          val xValue = xLabel.value as? Long
          val yValue = yLabel.value as? Double
          if (xValue != null && yValue != null && yValue.isFinite()) Pair(xValue, yValue) else null
        }
        val primaryXDataFiltered = primaryYDataPairs.map { it.first }
        val primaryYDataFiltered = primaryYDataPairs.map { it.second }

        if (isActive && primaryXDataFiltered.isNotEmpty() && primaryYDataFiltered.isNotEmpty() &&
          primaryXDataFiltered.size == primaryYDataFiltered.size
        ) {
          producer.runTransaction(animate = false) {
            lineSeries {
              series(x = primaryXDataFiltered, y = primaryYDataFiltered)
            }
            if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
              val secondaryDataPairs = secondaryGraphLines.points.mapNotNull { point ->
                val xValue = point.x.value as? Long
                val yValue = (point.y.value as? Number)?.toDouble()
                if (xValue != null && yValue != null && yValue.isFinite()) Pair(xValue, yValue) else null
              }
              if (secondaryDataPairs.isNotEmpty()) {
                lineSeries {
                  series(x = secondaryDataPairs.map { it.first }, y = secondaryDataPairs.map { it.second })
                }
              }
            }
          }
          super.handleIntent(GraphIntent.UpdateIsLoading(false))
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error setting up chart model producer", e)
      }
    }
    this.isInitialized = true
  }

  // ── Common ──

  private fun subscribeWeightUnit() {
    viewModelScope.launch {
      accountService.activeAccountFlow.map { it?.weightUnit }.distinctUntilChanged().drop(1).collect { weightUnit ->
        if (weightUnit != null)
          handleIntent(GraphIntent.UpdateWeightUnit(weightUnit))
      }
    }
  }

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

  private fun handleScroll(productType: ProductType, min: Long, max: Long, fallback: () -> Unit = {}) {
    val min = GraphUtil.getRelativeStart(segment, min)
    val max = GraphUtil.getRelativeEnd(segment, max)
    val ps = _state.value.forProduct(productType)
    scrollDebounceJob?.cancel()
    scrollDebounceJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val filteredData = ps.data.filter { it.getTimeStamp() in min..max }
        if (filteredData.isEmpty()) {
          fallback()
        } else {
          super.handleIntent(GraphIntent.UpdateProductTarget(productType, filteredData))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        AppLog.e(TAG, "Error handling scroll", e)
      }
    }
  }

  override fun provideInitialState(): GraphState = GraphState()
}
