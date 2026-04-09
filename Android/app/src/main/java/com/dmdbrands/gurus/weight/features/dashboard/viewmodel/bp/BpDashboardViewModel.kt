package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BpDashboardViewModel @Inject constructor(
  private val historyService: IHistoryService,
  private val entryService: IEntryService,
) : BaseDashboardViewModel<BpDashboardState, BaseGraphIntent>(
  reducer = BpDashboardReducer(),
) {

  companion object {
    private const val TAG = "BpDashboardVM"
  }

  override fun provideInitialState(): BpDashboardState = BpDashboardState()

  override fun onDependenciesReady() {
    initProducers()
    startGraphSubscriptions()
    subscribeProgress()
  }

  override fun handleIntent(intent: BaseGraphIntent) {
    if (intent is BpDashboardIntent) {
      when (intent) {
        is BpDashboardIntent.Refresh -> refresh()
        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  // ── Graph subscriptions (direct, no adapter) ──

  private fun startGraphSubscriptions() {
    viewModelScope.launch {
      historyService.getDailyGraphData(ProductSelection.BloodPressure)
        .map { (it as? GraphData.BloodPressure)?.data ?: emptyList() }
        .collect { entries ->
          val series = toBpSeries(entries)
          updateSegmentRanges(entries, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
          if (series.isNotEmpty()) {
            pushSeriesToProducer(dailyProducer, series)
          } else {
            pushEmptyProducer(dailyProducer)
          }
        }
    }
    viewModelScope.launch {
      historyService.getMonthlyGraphData(ProductSelection.BloodPressure)
        .map { (it as? GraphData.BloodPressure)?.data ?: emptyList() }
        .collect { entries ->
          val series = toBpSeries(entries)
          updateSegmentRanges(entries, listOf(GraphSegment.YEAR, GraphSegment.TOTAL))
          if (series.isNotEmpty()) {
            pushSeriesToProducer(monthlyProducer, series)
          } else {
            pushEmptyProducer(monthlyProducer)
          }
        }
    }
  }

  private fun toBpSeries(entries: List<PeriodBpmSummary>): List<SeriesData> {
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    if (sorted.isEmpty()) return emptyList()
    val timestamps = sorted.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    return listOf(
      SeriesData(timestamps, sorted.map { it.avgSystolic.toDouble() }),
      SeriesData(timestamps, sorted.map { it.avgDiastolic.toDouble() }),
      SeriesData(timestamps, sorted.map { it.avgPulse.toDouble() }),
    )
  }

  private fun refresh() {
    viewModelScope.launch {
      AppLog.d(TAG, "BP dashboard refresh started")
      setRefreshing(true)
      entryService.syncOperations()
      setRefreshing(false)
    }
  }

  private fun subscribeProgress() {
    viewModelScope.launch {
      entryService.progress.collect { handleIntent(BpDashboardIntent.SetProgress(it)) }
    }
  }
}
