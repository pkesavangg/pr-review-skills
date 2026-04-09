package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

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
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
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
    // Note: selectedMetric (WEIGHT vs HEIGHT toggle) is not persisted across process death.
    // This VM uses @AssistedInject (no SavedStateHandle), and the project does not use
    // SavedStateHandle anywhere. Since the default is WEIGHT, losing this preference on
    // process death is acceptable — the user just taps the toggle again.
  }

  // Cached raw entries
  private var latestDailyEntries: List<PeriodBabySummary> = emptyList()
  private var latestMonthlyEntries: List<PeriodBabySummary> = emptyList()

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
          rebuildAllProducers()
          return
        }

        is BabyDashboardIntent.SetWeightPercentile,
        is BabyDashboardIntent.SetHeightPercentile -> {
          super.handleIntent(intent)
          rebuildAllProducers()
          return
        }

        else -> {}
      }
    }
    super.handleIntent(intent)
  }

  // ── 2 subscriptions: collect → cache → update ranges → rebuild producer ──

  private fun startGraphSubscriptions() {
    val profileId = babyProduct.profile.id

    viewModelScope.launch {
      historyService.getBabyDailyGraphData(profileId).collect { entries ->
        latestDailyEntries = entries
        updateBabySegmentRanges(entries, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
        rebuildProducer(_state.value.dailyProducer, entries)
      }
    }

    viewModelScope.launch {
      historyService.getBabyMonthlyGraphData(profileId).collect { entries ->
        latestMonthlyEntries = entries
        updateBabySegmentRanges(entries, listOf(GraphSegment.YEAR, GraphSegment.TOTAL))
        rebuildProducer(_state.value.monthlyProducer, entries)
      }
    }
  }

  /** Baby-specific: chartMinX = birthDate, chartMaxX = end of data. */
  private fun updateBabySegmentRanges(entries: List<PeriodBabySummary>, segments: List<GraphSegment>) {
    if (entries.isEmpty()) {
      segments.forEach { seg -> updateSegmentState(seg) { it.copy(isEmptyGraph = true) } }
      return
    }
    val birthDate = babyProduct.profile.birthDate ?: entries.first().getTimeStamp()
    val endTs = entries.maxOf { it.getTimeStamp() }
    val targetData = entries.toImmutableList<PeriodSummary>()

    for (segment in segments) {
      val endX = GraphUtil.getEndRange(segment, endTs) ?: endTs
      val filteredTarget = entries.filter { it.getTimeStamp() in birthDate..endX }

      updateSegmentState(segment) {
        it.copy(
          data = targetData,
          target = filteredTarget.toImmutableList<PeriodSummary>(),
          chartMinX = birthDate.toDouble(),
          chartMaxX = endX.toDouble(),
          isEmptyGraph = false,
          startTimestamp = birthDate,
          endTimestamp = endTs,
        )
      }
    }
  }

  // ── Producer rebuild ──

  private fun rebuildAllProducers() {
    rebuildProducer(_state.value.dailyProducer, latestDailyEntries)
    rebuildProducer(_state.value.monthlyProducer, latestMonthlyEntries)
  }

  private fun rebuildProducer(producer: CartesianChartModelProducer, entries: List<PeriodBabySummary>) {
    val series = activeSeriesFor(entries)
    if (series.isEmpty()) {
      viewModelScope.launch { pushEmptyProducer(producer) }
      return
    }

    val percentile = _state.value.activePercentile
    viewModelScope.launch(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        // Always push percentile layer first (even empty placeholder)
        // so Vico layer order matches chart config (percentile=layer0, data=layer1)
        if (percentile != null) {
          lineSeries {
            percentile.allBands().forEach { band ->
              series(x = percentile.xTimestamps, y = band)
            }
          }
        } else {
          // Placeholder: single series matching data X range so layer exists
          val xValues = series.firstOrNull()?.xValues ?: listOf(0L)
          val yValues = xValues.map { 0.0 }
          lineSeries { series(x = xValues, y = yValues) }
        }
        lineSeries {
          series.forEach { s -> series(x = s.xValues, y = s.yValues) }
        }
      }
    }
  }

  // ── Series conversion ──

  private fun activeSeriesFor(entries: List<PeriodBabySummary>): List<SeriesData> =
    when (_state.value.selectedMetric) {
      BabyMetric.WEIGHT -> toWeightSeries(entries)
      BabyMetric.HEIGHT -> toHeightSeries(entries)
    }

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
      handleIntent(BabyDashboardIntent.SetWeightPercentile(weightSeries))
      handleIntent(BabyDashboardIntent.SetHeightPercentile(heightSeries))
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
