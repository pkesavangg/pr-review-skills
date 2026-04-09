package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.collections.immutable.toImmutableList
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.icu.util.Calendar

/**
 * Base ViewModel for all product dashboards. Provides:
 * - Shared segment range computation
 * - Shared producer transaction helper
 * - ScrollRange intent handling
 *
 * Producers live in the state (set via [initProducers]).
 * Each product VM subscribes to its own typed data flow, converts entries
 * to series inline, then calls [updateSegmentRanges] + [pushSeriesToProducer].
 */
abstract class BaseDashboardViewModel<S : BaseDashboardState, I : BaseGraphIntent>(
  reducer: IReducer<S, I>,
) : BaseIntentViewModel<S, I>(reducer) {

  companion object {
    private const val TAG = "BaseDashboardVM"
  }

  // ── Intent helpers ──

  @Suppress("UNCHECKED_CAST")
  protected fun updateSegmentState(segment: GraphSegment, update: (SegmentState) -> SegmentState) {
    super.handleIntent(BaseGraphIntent.UpdateSegment(segment, update) as I)
  }

  @Suppress("UNCHECKED_CAST")
  protected fun setRefreshing(isRefreshing: Boolean) {
    super.handleIntent(BaseGraphIntent.SetRefreshing(isRefreshing) as I)
  }

  // ── Shared: segment range computation ──

  /**
   * Compute segment ranges (minTarget, maxTarget, chartMinX, chartMaxX, etc.)
   * from entries and update segment states. Same logic for all products.
   */
  protected fun updateSegmentRanges(
    entries: List<PeriodSummary>,
    segments: List<GraphSegment>,
  ) {
    if (entries.isEmpty()) {
      segments.forEach { segment ->
        updateSegmentState(segment) { it.copy(isEmptyGraph = true) }
      }
      return
    }

    val timestamps = entries.map { it.getTimeStamp() }.sorted()
    val initialTimeStamp = timestamps.minOrNull()
    val endTimeStamp = timestamps.maxOrNull()
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

      val filteredTarget = entries.filter { it.getTimeStamp() in startX..endX }

      updateSegmentState(segment) {
        it.copy(
          data = entries.toImmutableList(),
          target = filteredTarget.toImmutableList(),
          chartMinX = chartMinX,
          chartMaxX = chartMaxX,
          isSingleWindow = isSingleWindow,
          isEmptyGraph = false,
          startTimestamp = initialTimeStamp,
          endTimestamp = endTimeStamp,
          visibleMin = it.visibleMin ?: startX,
          visibleMax = it.visibleMax ?: endX,
        )
      }
    }
  }

  // ── Shared: push series to producer ──

  protected suspend fun pushSeriesToProducer(
    producer: CartesianChartModelProducer,
    vararg seriesBlocks: List<SeriesData>,
  ) {
    withContext(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        seriesBlocks.forEach { block ->
          if (block.isNotEmpty()) {
            lineSeries {
              block.forEach { s -> series(x = s.xValues, y = s.yValues) }
            }
          }
        }
      }
    }
  }

  protected suspend fun pushEmptyProducer(producer: CartesianChartModelProducer) {
    withContext(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        lineSeries { series(listOf(0.0), listOf(0.0)) }
      }
    }
  }

  // ── ScrollRange handling (shared) ──

  override fun handleIntent(intent: I) {
    if (intent is BaseGraphIntent.ScrollRange && _state.value.markerIndex == null) {
      val adjMin = GraphUtil.getRelativeStart(intent.segment, intent.min)
      val adjMax = GraphUtil.getRelativeEnd(intent.segment, intent.max)
      val ss = _state.value.forSegment(intent.segment)
      val filteredData = ss.data.filter { it.getTimeStamp() in adjMin..adjMax }
      if (filteredData.isNotEmpty()) {
        @Suppress("UNCHECKED_CAST")
        super.handleIntent(BaseGraphIntent.UpdateSegmentTarget(intent.segment, filteredData) as I)
      } else {
        @Suppress("UNCHECKED_CAST")
        super.handleIntent(BaseGraphIntent.UpdateSegmentTarget(intent.segment, emptyList()) as I)
        intent.onFallback()
      }
    }
    super.handleIntent(intent)
  }
}
