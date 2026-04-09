package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import android.content.Context
import android.icu.util.Calendar
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
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
import kotlinx.coroutines.launch

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
  }

  private var latestDailyEntries: List<PeriodBabySummary> = emptyList()
  private var latestMonthlyEntries: List<PeriodBabySummary> = emptyList()

  override fun provideInitialState(): BabyDashboardState = BabyDashboardState()

  override fun onDependenciesReady() {
    handleIntent(BabyDashboardIntent.SetBabyProfile(babyProduct.profile))
    initProducers()
    loadPercentiles()
    startGraphSubscriptions()
  }

  override fun handleIntent(intent: BaseGraphIntent) {
    if (intent is BabyDashboardIntent) {
      when (intent) {
        is BabyDashboardIntent.Refresh -> refresh()
        is BabyDashboardIntent.SetSelectedMetric -> {
          super.handleIntent(intent)
          rebuildProducers()
          return
        }
        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  // ── 4 independent graph subscriptions (weight daily/monthly + height daily/monthly) ──

  private fun startGraphSubscriptions() {
    val profileId = babyProduct.profile.id
    val dailyFlow = historyService.getBabyDailyGraphData(profileId)
    val monthlyFlow = historyService.getBabyMonthlyGraphData(profileId)

    // Weight daily (WEEK/MONTH)
    viewModelScope.launch {
      dailyFlow.collect { entries ->
        latestDailyEntries = entries
        val segments = listOf(GraphSegment.WEEK, GraphSegment.MONTH)
        updateMetricSegments(BabyMetric.WEIGHT, entries, segments)
        if (_state.value.selectedMetric == BabyMetric.WEIGHT) {
          pushBabyProducer(dailyProducer, entries)
        }
      }
    }

    // Weight monthly (YEAR/TOTAL)
    viewModelScope.launch {
      monthlyFlow.collect { entries ->
        latestMonthlyEntries = entries
        val segments = listOf(GraphSegment.YEAR, GraphSegment.TOTAL)
        updateMetricSegments(BabyMetric.WEIGHT, entries, segments)
        if (_state.value.selectedMetric == BabyMetric.WEIGHT) {
          pushBabyProducer(monthlyProducer, entries)
        }
      }
    }

    // Height daily (WEEK/MONTH)
    viewModelScope.launch {
      dailyFlow.collect { entries ->
        val segments = listOf(GraphSegment.WEEK, GraphSegment.MONTH)
        updateMetricSegments(BabyMetric.HEIGHT, entries, segments)
        if (_state.value.selectedMetric == BabyMetric.HEIGHT) {
          pushBabyProducer(dailyProducer, entries)
        }
      }
    }

    // Height monthly (YEAR/TOTAL)
    viewModelScope.launch {
      monthlyFlow.collect { entries ->
        val segments = listOf(GraphSegment.YEAR, GraphSegment.TOTAL)
        updateMetricSegments(BabyMetric.HEIGHT, entries, segments)
        if (_state.value.selectedMetric == BabyMetric.HEIGHT) {
          pushBabyProducer(monthlyProducer, entries)
        }
      }
    }
  }

  // ── Segment state update (targets a specific metric via UpdateMetricSegment) ──

  private fun updateMetricSegments(
    metric: BabyMetric,
    entries: List<PeriodBabySummary>,
    segments: List<GraphSegment>,
  ) {
    if (entries.isEmpty()) {
      segments.forEach { seg ->
        handleIntent(BabyDashboardIntent.UpdateMetricSegment(metric, seg) { it.copy(isEmptyGraph = true) })
      }
      return
    }
    val timestamps = entries.map { it.getTimeStamp() }.sorted()
    val initialTs = timestamps.minOrNull()
    val endTs = timestamps.maxOrNull()
    val calendar = Calendar.getInstance()

    for (segment in segments) {
      val isSingleWindow = GraphUtil.isSingleWindow(segment, initialTs, endTs)

      val (startX, endX) = if (segment == GraphSegment.TOTAL) {
        val s = (initialTs ?: calendar.timeInMillis).let {
          Calendar.getInstance().apply { timeInMillis = it; add(Calendar.MONTH, -6) }.timeInMillis
        }
        val e = (endTs ?: calendar.timeInMillis).let {
          Calendar.getInstance().apply { timeInMillis = it; add(Calendar.MONTH, +6) }.timeInMillis
        }
        s to e
      } else {
        val s = GraphUtil.getRollingWindowStart(segment, endTs)
          ?: GraphUtil.getStartRange(segment, endTs)
          ?: calendar.timeInMillis
        s to (endTs ?: calendar.timeInMillis)
      }

      val chartMinX = if (segment == GraphSegment.TOTAL) startX.toDouble()
      else GraphUtil.getStartRange(segment, initialTs)?.toDouble() ?: startX.toDouble()

      val chartMaxX = if (segment == GraphSegment.TOTAL) {
        endX.toDouble()
      } else if (segment == GraphSegment.MONTH) {
        val ps = GraphUtil.getStartRange(segment, calendar.timeInMillis) ?: calendar.timeInMillis
        Calendar.getInstance().apply { timeInMillis = ps; add(Calendar.DAY_OF_YEAR, 30) }.timeInMillis.toDouble()
      } else {
        GraphUtil.getEndRange(segment, calendar.timeInMillis)?.toDouble() ?: endX.toDouble()
      }

      val filteredTarget = entries.filter { it.getTimeStamp() in startX..endX }

      handleIntent(BabyDashboardIntent.UpdateMetricSegment(metric, segment) {
        it.copy(
          data = entries.toImmutableList<PeriodSummary>(),
          target = filteredTarget.toImmutableList<PeriodSummary>(),
          minTarget = startX,
          maxTarget = endX,
          chartMinX = chartMinX,
          chartMaxX = chartMaxX,
          isSingleWindow = isSingleWindow,
          isEmptyGraph = false,
          startTimestamp = initialTs,
          endTimestamp = endTs,
        )
      })
    }
  }

  // ── Series conversion ──

  private fun toWeightSeries(entries: List<PeriodBabySummary>): List<SeriesData> {
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    val pairs = sorted.mapNotNull { e ->
      val ts = DateTimeConverter.isoToTimestamp(e.entryTimestamp)
      val w = e.avgWeightDecigrams?.let { it / 283.495 / 16.0 }
      if (w != null) ts to w else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  private fun toHeightSeries(entries: List<PeriodBabySummary>): List<SeriesData> {
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    val pairs = sorted.mapNotNull { e ->
      val ts = DateTimeConverter.isoToTimestamp(e.entryTimestamp)
      val h = e.avgLengthMillimeters?.let { it / 25.4 }
      if (h != null) ts to h else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  private fun activeSeriesFor(entries: List<PeriodBabySummary>): List<SeriesData> =
    when (_state.value.selectedMetric) {
      BabyMetric.WEIGHT -> toWeightSeries(entries)
      BabyMetric.HEIGHT -> toHeightSeries(entries)
    }

  // ── Producer push (percentile + data) ──

  private fun pushBabyProducer(
    producer: CartesianChartModelProducer,
    entries: List<PeriodBabySummary>,
  ) {
    val series = activeSeriesFor(entries)
    if (series.isEmpty()) {
      viewModelScope.launch { pushEmptyProducer(producer) }
      return
    }
    val percentile = _state.value.activePercentileSeries
    viewModelScope.launch(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        if (percentile != null) {
          lineSeries {
            percentile.allBands().forEach { band ->
              series(x = percentile.xTimestamps, y = band)
            }
          }
        }
        lineSeries {
          series.forEach { s -> series(x = s.xValues, y = s.yValues) }
        }
      }
    }
  }

  private fun rebuildProducers() {
    if (latestDailyEntries.isNotEmpty()) pushBabyProducer(dailyProducer, latestDailyEntries)
    if (latestMonthlyEntries.isNotEmpty()) pushBabyProducer(monthlyProducer, latestMonthlyEntries)
  }

  // ── Percentiles ──

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
