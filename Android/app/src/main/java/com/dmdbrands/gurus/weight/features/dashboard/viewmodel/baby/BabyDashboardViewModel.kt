package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import android.content.Context

@HiltViewModel(assistedFactory = BabyDashboardViewModel.Factory::class)
class BabyDashboardViewModel @AssistedInject constructor(
  @Assisted val babyProduct: ProductSelection.Baby,
  @ApplicationContext private val context: Context,
  private val entryReadService: IEntryReadService,
  private val entryService: IEntryService,
  private val userDataStore: UserDataStore,
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
    subscribeBabyWeightUnit()
    startGraphSubscriptions()
    loadPercentiles()
  }

  /**
   * The baby graph, header and percentile overlay all render in the account's baby weight unit
   * (My Kids setting). When it changes, re-derive the percentile overlay (kg/cm vs lb/in), the
   * seeded Y ranges and the plotted series so the change reflects immediately — the same way the
   * adult weight graph reacts to its unit. (MOB-1499)
   */
  private fun subscribeBabyWeightUnit() {
    viewModelScope.launch {
      userDataStore.babyWeightUnitForCurrentAccountFlow.distinctUntilChanged().collect { unit ->
        handleIntent(BabyDashboardIntent.SetBabyWeightUnit(unit))
        loadPercentiles()
        updateBabySegmentRanges(latestDailyEntries, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
        updateBabySegmentRanges(latestMonthlyEntries, listOf(GraphSegment.YEAR, GraphSegment.TOTAL))
        rebuildAllProducers()
      }
    }
  }

  /** Decigrams → display weight in the active unit (kg for metric, decimal lb otherwise). */
  private fun weightToDisplay(decigrams: Int): Double =
    if (_state.value.isMetric) ConversionTools.convertDecigramsToKgExact(decigrams.toDouble())
    else ConversionTools.convertDecigramsToLbExact(decigrams)

  /** Millimetres → display length in the active unit (cm for metric, inches otherwise). */
  private fun heightToDisplay(millimeters: Int): Double =
    if (_state.value.isMetric) ConversionTools.convertMmToCmExact(millimeters.toDouble())
    else ConversionTools.convertMmToInchesExact(millimeters)

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
      entryReadService.getBabyDailyGraphData(profileId).collect { raw ->
        // Empty-state (CTA + zeroed header, MOB-432) is driven by real readings only —
        // the synthetic birth point is profile metadata, not a logged reading.
        handleIntent(BabyDashboardIntent.SetIsEmpty(raw.isEmpty()))
        val entries = withBirthPoint(raw)
        latestDailyEntries = entries
        updateBabySegmentRanges(entries, listOf(GraphSegment.WEEK, GraphSegment.MONTH))
        rebuildProducer(_state.value.dailyProducer, entries)
      }
    }

    viewModelScope.launch {
      entryReadService.getBabyMonthlyGraphData(profileId).collect { raw ->
        val entries = withBirthPoint(raw)
        latestMonthlyEntries = entries
        AppLog.d(
          TAG,
          "YTDEBUG monthly recv size=${entries.size} " +
            "first=${entries.firstOrNull()?.entryTimestamp} w=${entries.firstOrNull()?.avgWeightDecigrams} " +
            "l=${entries.firstOrNull()?.avgLengthMillimeters} metric=${_state.value.selectedMetric}",
        )
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
      val bounds = computeBabySegmentBounds(segment, birthDate, firstDataTs, endTs, now)
      val filteredTarget = entries.filter { it.getTimeStamp() in bounds.startX..bounds.endX }
      val seed = computeBabySeedRange(
        entries = entries,
        startX = bounds.startX,
        endX = bounds.endX,
        filteredTarget = filteredTarget,
        selectedMetric = _state.value.selectedMetric,
        weightToDisplay = ::weightToDisplay,
        heightToDisplay = ::heightToDisplay,
      )

      AppLog.d(
        TAG,
        "YTDEBUG range seg=$segment single=${bounds.isSingleWindow} startX=${bounds.startX} endX=${bounds.endX} " +
          "cMin=${bounds.chartMinX} cMax=${bounds.chartMaxX} tgt=${filteredTarget.size} yv=${seed.yValueCount} seed=${seed.range} " +
          "ptTs=${entries.firstOrNull()?.getTimeStamp()}",
      )
      updateSegmentState(segment) {
        it.copy(
          data = targetData,
          target = filteredTarget.toImmutableList<PeriodSummary>(),
          chartMinX = bounds.chartMinX,
          chartMaxX = bounds.chartMaxX,
          isSingleWindow = bounds.isSingleWindow,
          isEmptyGraph = false,
          startTimestamp = firstDataTs,
          endTimestamp = endTs,
          visibleMin = it.visibleMin ?: bounds.startX,
          visibleMax = it.visibleMax ?: bounds.endX,
          seedMinY = seed.range?.first ?: it.seedMinY,
          seedMaxY = seed.range?.second ?: it.seedMaxY,
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
    AppLog.d(
      TAG,
      "YTDEBUG rebuild entries=${entries.size} series=${series.size} " +
        "pts=${series.firstOrNull()?.xValues?.size} firstX=${series.firstOrNull()?.xValues?.firstOrNull()} " +
        "firstY=${series.firstOrNull()?.yValues?.firstOrNull()} pct=${_state.value.activePercentile != null}",
    )
    if (series.isEmpty()) {
      viewModelScope.launch { pushEmptyProducer(producer) }
      return
    }

    val percentile = _state.value.activePercentile
    viewModelScope.launch(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        // Push the percentile band layer first (percentile=layer0, data=layer1) ONLY when a
        // series is available. When it isn't — e.g. a "Private" gender baby — we push the data
        // series alone and the chart config drops the percentile layer too (hasPercentile =
        // activePercentile != null), so no stray band/placeholder line is drawn (MOB-1537).
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

  // ── Birth point ──

  /** Prepends the birth measurement as a day-0 chart point for the active baby. */
  private fun withBirthPoint(entries: List<PeriodBabySummary>): List<PeriodBabySummary> =
    prependBirthPoint(babyProduct.profile, entries)

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
      val w = e.avgWeightDecigrams?.let { weightToDisplay(it) }
      if (w != null) ts to w else null
    }
    if (pairs.isEmpty()) return emptyList()
    return listOf(SeriesData(pairs.map { it.first }, pairs.map { it.second }))
  }

  private fun toHeightSeries(entries: List<PeriodBabySummary>): List<SeriesData> {
    val sorted = entries.sortedBy { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }
    val pairs = sorted.mapNotNull { e ->
      val ts = DateTimeConverter.isoToTimestamp(e.entryTimestamp)
      val h = e.avgLengthMillimeters?.let { heightToDisplay(it) }
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
      val isMetric = _state.value.isMetric
      val weightSeries = BabyPercentileHelper.getWeightPercentileSeries(profile.sex, birthDateMillis, isMetric)
      val heightSeries = BabyPercentileHelper.getLengthPercentileSeries(profile.sex, birthDateMillis, isMetric)
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

/**
 * babyApp parity: the growth curve starts at the birthday. If [profile] carries a birth
 * weight/length, prepend a synthetic day-0 [PeriodBabySummary] at the birthdate so the
 * chart's first point IS the birth measurement. Returns [entries] unchanged when the
 * profile has no birth measurement, the birthdate is unknown/unparseable, or a real
 * reading already lands on the birth day.
 */
internal fun prependBirthPoint(
  profile: BabyProfile,
  entries: List<PeriodBabySummary>,
): List<PeriodBabySummary> {
  val birthIso = profile.birthdate ?: return entries
  if (profile.birthWeightDecigrams == null && profile.birthLengthMillimeters == null) return entries
  val birthMillis = DateTimeConverter.isoToTimestamp(birthIso)
  if (birthMillis <= 0L) return entries
  val msPerDay = 86_400_000L
  val birthDay = birthMillis / msPerDay
  val hasReadingOnBirthDay = entries.any { it.getTimeStamp() / msPerDay == birthDay }
  if (hasReadingOnBirthDay) return entries
  val birthPoint = PeriodBabySummary(
    period = birthIso,
    entryTimestamp = birthIso,
    babyId = profile.id,
    avgWeightDecigrams = profile.birthWeightDecigrams,
    avgLengthMillimeters = profile.birthLengthMillimeters,
  )
  return listOf(birthPoint) + entries
}

/** Per-segment X-axis bounds for the baby chart (extracted from updateBabySegmentRanges). */
private data class BabySegmentBounds(
  val isSingleWindow: Boolean,
  val chartMinX: Double,
  val chartMaxX: Double,
  val startX: Long,
  val endX: Long,
)

/** Seed Y-range plus the count of finite positive values (kept for the YTDEBUG log). */
private data class BabySeed(
  val range: Pair<Double, Double>?,
  val yValueCount: Int,
)

/** Pure X-axis bounds computation for one baby-chart [segment]. */
private fun computeBabySegmentBounds(
  segment: GraphSegment,
  birthDate: Long,
  firstDataTs: Long,
  endTs: Long,
  now: Long,
): BabySegmentBounds {
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
  return BabySegmentBounds(isSingleWindow, chartMinX, chartMaxX, startX, endX)
}

/**
 * Pure seed Y-range computation for the baby chart. Matches ScrollAwareRangeProvider padding
 * (paddingEntries=1): include 1 entry just before and 1 entry just after the rolling window so
 * the seed Y range matches the runtime Y range exactly. Without this, an entry just outside the
 * window would expand the runtime range and cause a frame-1 → frame-2 slide on initial load.
 */
private fun computeBabySeedRange(
  entries: List<PeriodBabySummary>,
  startX: Long,
  endX: Long,
  filteredTarget: List<PeriodBabySummary>,
  selectedMetric: BabyMetric,
  // Unit-aware conversions supplied by the ViewModel (kg/cm for metric, lb/in otherwise) so the
  // seed Y range is scaled in the SAME unit the series is plotted in. (MOB-1499)
  weightToDisplay: (Int) -> Double,
  heightToDisplay: (Int) -> Double,
): BabySeed {
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

  val yValues: List<Double> = when (selectedMetric) {
    BabyMetric.WEIGHT -> seedSource.mapNotNull { e ->
      e.avgWeightDecigrams?.let { weightToDisplay(it) }
    }
    BabyMetric.HEIGHT -> seedSource.mapNotNull { e ->
      e.avgLengthMillimeters?.let { heightToDisplay(it) }
    }
  }.filter { it.isFinite() && it > 0.0 }

  val range: Pair<Double, Double>? = if (yValues.isNotEmpty()) {
    val scale = generateNiceScale(
      minValue = yValues.min(),
      maxValue = yValues.max(),
      goalWeight = 0.0,
      isWeightLessMode = false,
      targetTickCount = 4,
    )
    scale.min to scale.max
  } else null

  return BabySeed(range, yValues.size)
}
