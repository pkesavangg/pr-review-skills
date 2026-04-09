package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

  // Cached series for rebuild on metric switch
  private var weightDailySeries: List<SeriesData> = emptyList()
  private var weightMonthlySeries: List<SeriesData> = emptyList()
  private var heightDailySeries: List<SeriesData> = emptyList()
  private var heightMonthlySeries: List<SeriesData> = emptyList()

  override fun provideInitialState(): BabyDashboardState = BabyDashboardState()

  init {
    loadPercentiles()
  }

  override fun onDependenciesReady() {
    handleIntent(BabyDashboardIntent.SetBabyProfile(babyProduct.profile))
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

        is BabyDashboardIntent.SetPercentile -> {
          super.handleIntent(intent)
          // Percentiles loaded async — re-push so producer includes bands
          if (intent.metric == _state.value.selectedMetric) rebuildProducers()
          return
        }

        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  // ── 4 subscriptions — update segment states + cache series ──

  private fun startGraphSubscriptions() {
    val profileId = babyProduct.profile.id
    val dailyFlow = historyService.getBabyDailyGraphData(profileId)
    val monthlyFlow = historyService.getBabyMonthlyGraphData(profileId)
    val dailySegments = listOf(GraphSegment.WEEK, GraphSegment.MONTH)
    val monthlySegments = listOf(GraphSegment.YEAR, GraphSegment.TOTAL)

    // Weight daily
    viewModelScope.launch {
      dailyFlow.map { toWeightSeries(it) }
        .distinctUntilChanged()
        .collect { series ->
          weightDailySeries = series
          updateMetricSegments(BabyMetric.WEIGHT, series, dailySegments)
          if (_state.value.selectedMetric == BabyMetric.WEIGHT) {
            pushActiveToProducer(_state.value.dailyProducer, daily = true)
          }
        }
    }

    // Weight monthly
    viewModelScope.launch {
      monthlyFlow.map { toWeightSeries(it) }
        .distinctUntilChanged()
        .collect { series ->
          weightMonthlySeries = series
          updateMetricSegments(BabyMetric.WEIGHT, series, monthlySegments)
          if (_state.value.selectedMetric == BabyMetric.WEIGHT) {
            pushActiveToProducer(_state.value.monthlyProducer, daily = false)
          }
        }
    }

    // Height daily
    viewModelScope.launch {
      dailyFlow.map { toHeightSeries(it) }
        .distinctUntilChanged()
        .collect { series ->
          heightDailySeries = series
          updateMetricSegments(BabyMetric.HEIGHT, series, dailySegments)
          if (_state.value.selectedMetric == BabyMetric.HEIGHT) {
            pushActiveToProducer(_state.value.dailyProducer, daily = true)
          }
        }
    }

    // Height monthly
    viewModelScope.launch {
      monthlyFlow.map { toHeightSeries(it) }
        .distinctUntilChanged()
        .collect { series ->
          heightMonthlySeries = series
          updateMetricSegments(BabyMetric.HEIGHT, series, monthlySegments)
          if (_state.value.selectedMetric == BabyMetric.HEIGHT) {
            pushActiveToProducer(_state.value.monthlyProducer, daily = false)
          }
        }
    }
  }

  private fun rebuildProducers() {
    pushActiveToProducer(_state.value.dailyProducer, daily = true)
    pushActiveToProducer(_state.value.monthlyProducer, daily = false)
  }

  // ── Push active metric's series + percentile to a shared producer ──

  private fun pushActiveToProducer(producer: CartesianChartModelProducer, daily: Boolean) {
    val metric = _state.value.selectedMetric
    val series = when {
      metric == BabyMetric.WEIGHT && daily -> weightDailySeries
      metric == BabyMetric.WEIGHT && !daily -> weightMonthlySeries
      metric == BabyMetric.HEIGHT && daily -> heightDailySeries
      else -> heightMonthlySeries
    }

    if (series.isEmpty()) {
      viewModelScope.launch { pushEmptyProducer(producer) }
      return
    }

    val percentile = _state.value.metricState(metric).percentileSeries
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

  // ── Segment state update ──

  private fun updateMetricSegments(
    metric: BabyMetric,
    series: List<SeriesData>,
    segments: List<GraphSegment>,
  ) {
    if (series.isEmpty() || series.all { it.xValues.isEmpty() }) {
      segments.forEach { seg ->
        handleIntent(BabyDashboardIntent.UpdateMetricSegment(metric, seg) { it.copy(isEmptyGraph = true) })
      }
      return
    }

    val birthDate = babyProduct.profile.birthDate ?: series.first().xValues.min()
    val endTs = series.first().xValues.max()

    for (segment in segments) {
      val endX = GraphUtil.getEndRange(segment, endTs) ?: endTs

      handleIntent(
        BabyDashboardIntent.UpdateMetricSegment(metric, segment) {
          it.copy(
            chartMinX = birthDate.toDouble(),
            chartMaxX = endX.toDouble(),
            isEmptyGraph = false,
            startTimestamp = birthDate,
            endTimestamp = endTs,
          )
        },
      )
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
