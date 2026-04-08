package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.collections.immutable.toImmutableList
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.icu.util.Calendar

/**
 * Base ViewModel for all product dashboards. Manages:
 * - 2 producers (daily + monthly) — stable, never recreated
 * - 4 segment states (chart UI: ranges, markers, empty flag)
 * - Graph data subscriptions via adapter pattern
 *
 * Subclasses provide data flows and handle product-specific content.
 * Product data (entries, targets) lives in product-specific state, not here.
 */
abstract class BaseDashboardViewModel<S : BaseDashboardState, I : IReducer.Intent>(
  reducer: IReducer<S, I>,
) : BaseIntentViewModel<S, I>(reducer) {

  companion object {
    private const val TAG = "BaseDashboardVM"
  }

  abstract val adapter: GraphDataAdapter
  abstract fun getDailyDataFlow(): Flow<GraphData>
  abstract fun getMonthlyDataFlow(): Flow<GraphData>

  /** Subclass implements — updates a segment state entry via its own intent/reducer. */
  abstract fun updateSegmentState(segment: GraphSegment, update: (SegmentState) -> SegmentState)

  /** Subclass implements — stores the stable producers in its state. */
  abstract fun setProducers(daily: CartesianChartModelProducer, monthly: CartesianChartModelProducer)

  abstract fun setRefreshing(isRefreshing: Boolean)

  // refresh() and setSelectedSegment() are handled via product-specific intents
  // No abstract methods needed — composables dispatch intents directly

  /**
   * Called when graph data arrives. Subclass handles product-specific data storage
   * (e.g., weight stores PeriodBodyScaleSummary list, BP stores PeriodBpmSummary list).
   * Base only handles chart ranges and producer transactions.
   */
  open fun onGraphDataReceived(graphData: GraphData, segments: List<GraphSegment>) {}

  private val _dailyProducer = CartesianChartModelProducer()
  private val _monthlyProducer = CartesianChartModelProducer()
  private var dailyDataJob: Job? = null
  private var monthlyDataJob: Job? = null

  protected fun startGraphSubscriptions() {
    setProducers(_dailyProducer, _monthlyProducer)
    observeDailyData()
    observeMonthlyData()
  }

  private fun observeDailyData() {
    dailyDataJob?.cancel()
    dailyDataJob = viewModelScope.launch {
      getDailyDataFlow().collect { graphData ->
        pushGraphData(graphData, _dailyProducer, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
      }
    }
  }

  private fun observeMonthlyData() {
    monthlyDataJob?.cancel()
    monthlyDataJob = viewModelScope.launch {
      getMonthlyDataFlow().collect { graphData ->
        pushGraphData(graphData, _monthlyProducer, listOf(GraphSegment.YEAR, GraphSegment.TOTAL))
      }
    }
  }

  private suspend fun pushGraphData(
    graphData: GraphData,
    producer: CartesianChartModelProducer,
    segments: List<GraphSegment>,
  ) {
    val seriesList = adapter.toLineSeries(graphData)

    if (seriesList.isEmpty() || seriesList.all { it.xValues.isEmpty() }) {
      segments.forEach { segment ->
        updateSegmentState(segment) { it.copy(isEmptyGraph = true) }
      }
      withContext(Dispatchers.Main) {
        producer.runTransaction(animate = false) {
          lineSeries { series(listOf(0.0), listOf(0.0)) }
        }
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

      updateSegmentState(segment) {
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
      }
    }

    // Let subclass handle product-specific data storage
    onGraphDataReceived(graphData, segments)

    withContext(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        lineSeries {
          seriesList.forEach { s -> series(x = s.xValues, y = s.yValues) }
        }
      }
    }
  }

  /** Update marker index — product-level, not segment-level. Subclass dispatches its own intent. */
  abstract fun updateMarkerIndex(markerIndex: Double?)

  fun getProducerForSegment(segment: GraphSegment): CartesianChartModelProducer {
    return if (segment == GraphSegment.WEEK || segment == GraphSegment.MONTH) _dailyProducer else _monthlyProducer
  }

  /**
   * Handle graph intents from GraphView. Single entry point for all graph interactions.
   * Composables call this instead of direct VM methods.
   */
  fun handleGraphIntent(intent: BaseGraphIntent) {
    when (intent) {
      is BaseGraphIntent.ScrollRange -> {
        val adjMin = GraphUtil.getRelativeStart(intent.segment, intent.min)
        val adjMax = GraphUtil.getRelativeEnd(intent.segment, intent.max)
        val ss = _state.value.forSegment(intent.segment)
        val filteredData = ss.data.filter { it.getTimeStamp() in adjMin..adjMax }
        if (filteredData.isNotEmpty()) {
          updateSegmentState(intent.segment) { it.copy(target = filteredData.toImmutableList()) }
        }
      }
      is BaseGraphIntent.UpdateMarkerIndex -> updateMarkerIndex(intent.markerIndex)
      is BaseGraphIntent.UpdateIsEmptyGraph -> updateSegmentState(intent.segment) { it.copy(isEmptyGraph = intent.isEmpty) }
      is BaseGraphIntent.UpdateSegmentTarget -> updateSegmentState(intent.segment) { it.copy(target = intent.target.toImmutableList()) }
    }
  }
}
