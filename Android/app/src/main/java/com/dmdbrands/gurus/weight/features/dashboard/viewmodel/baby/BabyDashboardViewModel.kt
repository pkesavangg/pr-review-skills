package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import android.content.Context
import android.icu.util.Calendar
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.BabyGraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.BabyHeightGraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = BabyDashboardViewModel.Factory::class)
class BabyDashboardViewModel @AssistedInject constructor(
  @Assisted val babyProduct: ProductSelection.Baby,
  @ApplicationContext private val context: Context,
  private val historyService: IHistoryService,
  private val entryService: IEntryService,
) : BaseDashboardViewModel<BabyDashboardState, BaseGraphIntent>(
  reducer = BabyDashboardReducer(),
) {

  @AssistedFactory
  interface Factory {
    fun create(babyProduct: ProductSelection.Baby): BabyDashboardViewModel
  }

  companion object {
    private const val TAG = "BabyDashboardVM"
    // Note: selectedMetric (WEIGHT vs HEIGHT toggle) is not persisted across process death.
    // This VM uses @AssistedInject (no SavedStateHandle), and the project does not use
    // SavedStateHandle anywhere. Since the default is WEIGHT, losing this preference on
    // process death is acceptable — the user just taps the toggle again.
  }

  private val weightAdapter: GraphDataAdapter = BabyGraphDataAdapter()
  private val heightAdapter: GraphDataAdapter = BabyHeightGraphDataAdapter()

  override val adapter: GraphDataAdapter get() = adapterForMetric(_state.value.selectedMetric)

  private fun adapterForMetric(metric: BabyMetric) = when (metric) {
    BabyMetric.WEIGHT -> weightAdapter
    BabyMetric.HEIGHT -> heightAdapter
  }

  // Cached raw data for rebuilding producers on metric switch
  private var latestDailyData: GraphData? = null
  private var latestMonthlyData: GraphData? = null

  private var dailyDataJob: Job? = null
  private var monthlyDataJob: Job? = null

  override fun getDailyDataFlow(): Flow<GraphData> =
    historyService.getDailyGraphData(babyProduct)

  override fun getMonthlyDataFlow(): Flow<GraphData> =
    historyService.getMonthlyGraphData(babyProduct)

  override fun provideInitialState(): BabyDashboardState = BabyDashboardState()

  override fun onDependenciesReady() {
    handleIntent(BabyDashboardIntent.SetBabyProfile(babyProduct.profile))
    loadPercentiles()
    startBabyGraphSubscriptions()
  }

  override fun handleIntent(intent: BaseGraphIntent) {
    if (intent is BabyDashboardIntent) {
      when (intent) {
        is BabyDashboardIntent.Refresh -> refresh()
        is BabyDashboardIntent.SetSelectedMetric -> {
          // Reduce first (flips selectedMetric), then rebuild producers
          super.handleIntent(intent)
          rebuildProducersForMetric(intent.metric)
          return
        }
        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  // ── Own subscriptions — push segment data to both metric maps ──

  private fun startBabyGraphSubscriptions() {
    // Producers come from provideInitialState() defaults — stable, never swapped.
    dailyDataJob?.cancel()
    dailyDataJob = viewModelScope.launch {
      getDailyDataFlow().collect { graphData ->
        latestDailyData = graphData
        val segments = listOf(GraphSegment.WEEK, GraphSegment.MONTH)
        // Update segment data for BOTH metrics
        updateMetricSegments(graphData, weightAdapter, BabyMetric.WEIGHT, segments)
        updateMetricSegments(graphData, heightAdapter, BabyMetric.HEIGHT, segments)
        // Push active metric to producer
        pushToProducer(_state.value.dailyProducer, graphData, segments)
      }
    }
    monthlyDataJob?.cancel()
    monthlyDataJob = viewModelScope.launch {
      getMonthlyDataFlow().collect { graphData ->
        latestMonthlyData = graphData
        val segments = listOf(GraphSegment.YEAR, GraphSegment.TOTAL)
        updateMetricSegments(graphData, weightAdapter, BabyMetric.WEIGHT, segments)
        updateMetricSegments(graphData, heightAdapter, BabyMetric.HEIGHT, segments)
        pushToProducer(_state.value.monthlyProducer, graphData, segments)
      }
    }
  }

  /** Update a metric's segment states (ranges, targets) without touching producers. */
  private fun updateMetricSegments(
    graphData: GraphData,
    adapter: GraphDataAdapter,
    metric: BabyMetric,
    segments: List<GraphSegment>,
  ) {
    val seriesList = adapter.toLineSeries(graphData)
    if (seriesList.isEmpty() || seriesList.all { it.xValues.isEmpty() }) {
      segments.forEach { seg ->
        handleIntent(BabyDashboardIntent.UpdateMetricSegment(metric, seg) { it.copy(isEmptyGraph = true) })
      }
      return
    }

    val timestamps = adapter.getTimestamps(graphData).sorted()
    val initialTimeStamp = timestamps.minOrNull()
    val endTimeStamp = timestamps.maxOrNull()
    val targetData = adapter.toTargetData(graphData)
    val calendar = Calendar.getInstance()

    for (segment in segments) {
      val isSingleWindow = GraphUtil.isSingleWindow(segment, initialTimeStamp, endTimeStamp)

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

      val filteredTarget = targetData.filter { it.getTimeStamp() in startX..endX }

      handleIntent(
        BabyDashboardIntent.UpdateMetricSegment(metric, segment) {
          it.copy(
            data = targetData.toImmutableList(),
            target = filteredTarget.toImmutableList(),
            minTarget = startX,
            maxTarget = endX,
            chartMinX = chartMinX,
            chartMaxX = chartMaxX,
            isSingleWindow = isSingleWindow,
            isEmptyGraph = false,
            startTimestamp = initialTimeStamp,
            endTimestamp = endTimeStamp,
          )
        },
      )
    }
  }

  /** Push active metric's data + percentile to a producer. */
  private fun pushToProducer(
    producer: CartesianChartModelProducer,
    graphData: GraphData,
    segments: List<GraphSegment>,
  ) {
    val activeMetric = _state.value.selectedMetric
    val activeAdapter = adapterForMetric(activeMetric)
    val seriesList = activeAdapter.toLineSeries(graphData)

    if (seriesList.isEmpty() || seriesList.all { it.xValues.isEmpty() }) {
      viewModelScope.launch(Dispatchers.Main) {
        producer.runTransaction(animate = false) {
          lineSeries { series(listOf(0.0), listOf(0.0)) }
        }
      }
      return
    }

    val percentileSeries = _state.value.activePercentileSeries

    // Two lineSeries blocks → two Vico layers. Percentile behind, data on top.
    viewModelScope.launch(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        if (percentileSeries != null) {
          lineSeries {
            percentileSeries.allBands().forEach { band ->
              series(x = percentileSeries.xTimestamps, y = band)
            }
          }
        }
        lineSeries {
          seriesList.forEach { s -> series(x = s.xValues, y = s.yValues) }
        }
      }
    }
  }

  /** Rebuild producers when switching between weight/height. */
  private fun rebuildProducersForMetric(metric: BabyMetric) {
    val daily = latestDailyData
    val monthly = latestMonthlyData
    if (daily != null) pushToProducer(_state.value.dailyProducer, daily, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
    if (monthly != null) pushToProducer(_state.value.monthlyProducer, monthly, listOf(GraphSegment.YEAR, GraphSegment.TOTAL))
  }

  private fun loadPercentiles() {
    val profile = babyProduct.profile
    val birthDateMillis = profile.birthdate?.let {
      com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter.isoToTimestamp(it)
    } ?: return
    viewModelScope.launch(Dispatchers.IO) {
      BabyPercentileHelper.loadIfNeeded(context)
      val weightSeries = BabyPercentileHelper.getWeightPercentileSeries(profile.sex, birthDateMillis)
      val heightSeries = BabyPercentileHelper.getLengthPercentileSeries(profile.sex, birthDateMillis)
      handleIntent(BabyDashboardIntent.SetPercentile(BabyMetric.WEIGHT, weightSeries))
      handleIntent(BabyDashboardIntent.SetPercentile(BabyMetric.HEIGHT, heightSeries))
    }
  }

  private fun refresh() {
    viewModelScope.launch {
      AppLog.d(TAG, "Baby dashboard refresh started")
      setRefreshing(true)
      entryService.syncOperations()
      setRefreshing(false)
    }
  }
}
