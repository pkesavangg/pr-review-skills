package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

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
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
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
import android.content.Context

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

  override fun provideInitialState(): BabyDashboardState = BabyDashboardState()

  override fun onDependenciesReady() {
    handleIntent(BabyDashboardIntent.SetBabyProfile(babyProduct.profile))
    loadPercentiles()
    startGraphSubscriptions()
  }

  override fun handleIntent(intent: BaseGraphIntent) {
    if (intent is BabyDashboardIntent) {
      when (intent) {
        is BabyDashboardIntent.Refresh -> refresh()
        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  // ── 4 independent graph subscriptions ──
  // Each pushes to its own metric's producer. No rebuild on metric switch.

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
        .collect { series -> updateAndPush(BabyMetric.WEIGHT, series, dailySegments, daily = true) }
    }

    // Weight monthly
    viewModelScope.launch {
      monthlyFlow.map { toWeightSeries(it) }
        .distinctUntilChanged()
        .collect { series -> updateAndPush(BabyMetric.WEIGHT, series, monthlySegments, daily = false) }
    }

    // Height daily
    viewModelScope.launch {
      dailyFlow.map { toHeightSeries(it) }
        .distinctUntilChanged()
        .collect { series -> updateAndPush(BabyMetric.HEIGHT, series, dailySegments, daily = true) }
    }

    // Height monthly
    viewModelScope.launch {
      monthlyFlow.map { toHeightSeries(it) }
        .distinctUntilChanged()
        .collect { series -> updateAndPush(BabyMetric.HEIGHT, series, monthlySegments, daily = false) }
    }
  }

  // ── Update segment state + push to producer ──

  private fun updateAndPush(
    metric: BabyMetric,
    series: List<SeriesData>,
    segments: List<GraphSegment>,
    daily: Boolean,
  ) {
    val metricState = _state.value.metricState(metric)
    val producer = if (daily) metricState.dailyProducer else metricState.monthlyProducer

    if (series.isEmpty() || series.all { it.xValues.isEmpty() }) {
      segments.forEach { seg ->
        handleIntent(BabyDashboardIntent.UpdateMetricSegment(metric, seg) { it.copy(isEmptyGraph = true) })
      }
      viewModelScope.launch { pushEmptyProducer(producer) }
      return
    }

    // Baby range: birthDate → end of data
    val birthDate = babyProduct.profile.birthDate ?: series.first().xValues.min()
    val endTs = series.first().xValues.max()

    for (segment in segments) {
      val startX = birthDate
      val endX = GraphUtil.getEndRange(segment, endTs) ?: endTs
      val chartMinX = birthDate.toDouble()
      val chartMaxX = endX.toDouble()

      handleIntent(
        BabyDashboardIntent.UpdateMetricSegment(metric, segment) {
          it.copy(
            chartMinX = chartMinX,
            chartMaxX = chartMaxX,
            isEmptyGraph = false,
            startTimestamp = birthDate,
            endTimestamp = endTs,
          )
        },
      )
    }

    // Push to producer
    viewModelScope.launch(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        lineSeries {
          series.forEach { s -> series(x = s.xValues, y = s.yValues) }
        }
      }
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
