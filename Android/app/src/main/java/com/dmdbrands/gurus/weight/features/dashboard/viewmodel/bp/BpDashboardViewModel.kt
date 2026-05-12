package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

@HiltViewModel
class BpDashboardViewModel @Inject constructor(
  private val entryReadService: IEntryReadService,
  private val entryService: IEntryService,
) : BaseDashboardViewModel<BpDashboardState, BaseGraphIntent>(
  reducer = BpDashboardReducer(),
) {

  companion object {
    private const val TAG = "BpDashboardVM"
    private const val LAST_READINGS_COUNT = 3
  }

  override fun provideInitialState(): BpDashboardState = BpDashboardState()

  override fun onDependenciesReady() {
    startGraphSubscriptions()
    subscribeProgress()
    subscribeLastReadings()
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
      entryReadService.getDailyGraphData(ProductSelection.BloodPressure)
        .map { (it as? GraphData.BloodPressure)?.data ?: emptyList() }
        .collect { entries ->
          val series = toBpSeries(entries)
          updateSegmentRanges(entries, listOf(GraphSegment.WEEK, GraphSegment.MONTH)) { data ->
            data.filterIsInstance<PeriodBpmSummary>()
              .flatMap { listOf(it.avgSystolic.toDouble(), it.avgDiastolic.toDouble(), it.avgPulse.toDouble()) }
              .filter { it > 0.0 }
          }
          if (series.isNotEmpty()) {
            pushSeriesToProducer(_state.value.dailyProducer, series)
          } else {
            pushEmptyProducer(_state.value.dailyProducer)
          }
        }
    }
    viewModelScope.launch {
      entryReadService.getMonthlyGraphData(ProductSelection.BloodPressure)
        .map { (it as? GraphData.BloodPressure)?.data ?: emptyList() }
        .collect { entries ->
          val series = toBpSeries(entries)
          updateSegmentRanges(entries, listOf(GraphSegment.YEAR, GraphSegment.TOTAL)) { data ->
            data.filterIsInstance<PeriodBpmSummary>()
              .flatMap { listOf(it.avgSystolic.toDouble(), it.avgDiastolic.toDouble(), it.avgPulse.toDouble()) }
              .filter { it > 0.0 }
          }
          if (series.isNotEmpty()) {
            pushSeriesToProducer(_state.value.monthlyProducer, series)
          } else {
            pushEmptyProducer(_state.value.monthlyProducer)
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
      entryReadService.bpProgress().collect { handleIntent(BpDashboardIntent.SetProgress(it)) }
    }
  }

  /**
   * Last N per-day BP averages for the three-reading-average card and sheet.
   * Account-scoped (not window-scoped) — computes the sys/dia/pulse mean across the
   * returned rows and pushes both rows + averages into state for the UI to consume.
   */
  private fun subscribeLastReadings() {
    viewModelScope.launch {
      entryReadService.getBpmLastNDayEntries(LAST_READINGS_COUNT).collect { rows ->
        val readings = if (rows.isEmpty()) {
          BpLastReadings()
        } else {
          BpLastReadings(
            entries = rows,
            averageSystolic = rows.map { it.avgSystolic }.average().roundToInt(),
            averageDiastolic = rows.map { it.avgDiastolic }.average().roundToInt(),
            averagePulse = rows.map { it.avgPulse }.average().roundToInt(),
          )
        }
        handleIntent(BpDashboardIntent.SetLastReadings(readings))
      }
    }
  }
}
