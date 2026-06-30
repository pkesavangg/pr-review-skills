package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
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
  private val entryReadService: IEntryReadService,
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

  // Cached raw entries
  private var latestDailyEntries: List<PeriodBabySummary> = emptyList()
  private var latestMonthlyEntries: List<PeriodBabySummary> = emptyList()

  override fun provideInitialState(): BabyDashboardState = BabyDashboardState()

  override fun onDependenciesReady() {
    handleIntent(BabyDashboardIntent.SetBabyProfile(babyProduct.profile))
    startGraphSubscriptions()
    loadPercentiles()
  }

  override fun handleIntent(intent: BaseGraphIntent) {
    if (intent is BabyDashboardIntent) {
      when (intent) {
        is BabyDashboardIntent.Refresh -> refresh()
        is BabyDashboardIntent.OnConnectDevice ->
          viewModelScope.launch { navigationService.navigateTo(AppRoute.AccountSettings.MyDevices) }
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
      entryReadService.getBabyDailyGraphData(profileId).collect { entries ->
        latestDailyEntries = entries
        // Drives the below-chart CONNECT DEVICE CTA + zeroed header (MOB-432).
        handleIntent(BabyDashboardIntent.SetIsEmpty(entries.isEmpty()))
        updateBabySegmentRanges(entries, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
        rebuildProducer(_state.value.dailyProducer, entries)
      }
    }

    viewModelScope.launch {
      entryReadService.getBabyMonthlyGraphData(profileId).collect { entries ->
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
    val birthDate = babyProduct.profile.birthdate?.let {
      DateTimeConverter.isoToTimestamp(it)
    } ?: entries.first().getTimeStamp()
    val firstDataTs = entries.minOf { it.getTimeStamp() }
    val endTs = entries.maxOf { it.getTimeStamp() }
    // The scroll domain extends to the CURRENT period end (this week/month/year), like the
    // weight graph — so the user can scroll from the data up to "now", never into the future.
    val now = System.currentTimeMillis()
    val targetData = entries.toImmutableList<PeriodSummary>()

    for (segment in segments) {
      val isSingleWindow = GraphUtil.isSingleWindow(segment, firstDataTs, endTs)

      // Canonical chart-wide X bounds — the same rule the weight/BP graph uses
      // (GraphUtil.computeChartXBounds): TOTAL pads the data extents by ±6 months; MONTH
      // reaches into the current month; WEEK/YEAR run from the oldest entry's window start to
      // the current period end. chartMinX is the EARLIEST data's window start (NOT the
      // birthdate — anchoring to birth collapsed the scroll domain and clipped pre-birth test
      // entries), except TOTAL which anchors to birth so the growth curve starts at day 0.
      val (boundMin, boundMax) = GraphUtil.computeChartXBounds(segment, firstDataTs, endTs, now)
      val chartMinX = when (segment) {
        GraphSegment.TOTAL -> minOf(birthDate, boundMin ?: firstDataTs)
        else -> boundMin ?: firstDataTs
      }.toDouble()
      val chartMaxX = (boundMax ?: endTs).toDouble()

      // Initial visible window:
      //  • TOTAL — the whole padded domain (it fits everything; not scrollable).
      //  • Single-window data (e.g. one month of entries, which aggregates to ONE monthly
      //    point on YEAR/TOTAL) — the FULL calendar window (week/month/year) so the lone
      //    point lands at its true position mid-chart instead of pinned to the right edge,
      //    which read as an empty graph. (MOB-592)
      //  • Otherwise — the latest rolling window, scrollable back to older data.
      val (startX, endX) = when {
        segment == GraphSegment.TOTAL -> chartMinX.toLong() to chartMaxX.toLong()
        isSingleWindow -> (GraphUtil.getStartRange(segment, endTs) ?: firstDataTs) to
          (GraphUtil.getEndRange(segment, endTs) ?: endTs)
        else -> (
          GraphUtil.getRollingWindowStart(segment, endTs)
            ?: GraphUtil.getStartRange(segment, endTs)
            ?: now
          ) to endTs
      }
      val filteredTarget = entries.filter { it.getTimeStamp() in startX..endX }

      // Match ScrollAwareRangeProvider padding (paddingEntries=1): include 1 entry just
      // before and 1 entry just after the rolling window so seed Y range matches the
      // runtime Y range exactly. Without this, an entry just outside the window would
      // expand the runtime range and cause a frame-1 → frame-2 slide on initial load.
      val seedSource = run {
        val sorted = entries.sortedBy { it.getTimeStamp() }
        val firstIdx = sorted.indexOfFirst { it.getTimeStamp() >= startX }
        val lastIdx = sorted.indexOfLast { it.getTimeStamp() <= endX }
        if (firstIdx < 0 || lastIdx < 0 || firstIdx > lastIdx) {
          filteredTarget
        } else {
          val from = (firstIdx - 1).coerceAtLeast(0)
          val to = (lastIdx + 1).coerceAtMost(sorted.lastIndex)
          sorted.subList(from, to + 1)
        }
      }

      val yValues: List<Double> = when (_state.value.selectedMetric) {
        BabyMetric.WEIGHT -> seedSource.mapNotNull { e ->
          e.avgWeightDecigrams?.let { ConversionTools.convertDecigramsToLbExact(it) }
        }
        BabyMetric.HEIGHT -> seedSource.mapNotNull { e ->
          e.avgLengthMillimeters?.let { it / 25.4 }
        }
      }.filter { it.isFinite() && it > 0.0 }

      val seed: Pair<Double, Double>? = if (yValues.isNotEmpty()) {
        val scale = generateNiceScale(
          minValue = yValues.min(),
          maxValue = yValues.max(),
          goalWeight = 0.0,
          isWeightLessMode = false,
          targetTickCount = 4,
        )
        scale.min to scale.max
      } else null

      updateSegmentState(segment) {
        it.copy(
          data = targetData,
          target = filteredTarget.toImmutableList<PeriodSummary>(),
          chartMinX = chartMinX,
          chartMaxX = chartMaxX,
          isSingleWindow = isSingleWindow,
          isEmptyGraph = false,
          startTimestamp = firstDataTs,
          endTimestamp = endTs,
          visibleMin = it.visibleMin ?: startX,
          visibleMax = it.visibleMax ?: endX,
          seedMinY = seed?.first ?: it.seedMinY,
          seedMaxY = seed?.second ?: it.seedMaxY,
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
      val w = e.avgWeightDecigrams?.let { ConversionTools.convertDecigramsToLbExact(it) }
      if (w != null) ts to w else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  private fun toHeightSeries(entries: List<PeriodBabySummary>): List<SeriesData> {
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    val pairs = sorted.mapNotNull { e ->
      val ts = DateTimeConverter.isoToTimestamp(e.entryTimestamp)
      val h = e.avgLengthMillimeters?.let { ConversionTools.convertMmToInchesExact(it) }
      if (h != null) ts to h else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  // ── Percentiles ──

  private fun loadPercentiles() {
    val profile = babyProduct.profile
    val birthDateMillis = profile.birthdate?.let {
      DateTimeConverter.isoToTimestamp(it)
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
