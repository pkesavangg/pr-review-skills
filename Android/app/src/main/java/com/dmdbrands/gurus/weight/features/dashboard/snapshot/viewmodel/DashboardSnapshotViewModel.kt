package com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.strings.DashboardSnapshotStrings
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class DashboardSnapshotViewModel @Inject constructor(
  @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
  private val entryReadService: IEntryReadService,
  private val accountService: IAccountService,
) : BaseIntentViewModel<DashboardSnapshotState, DashboardSnapshotIntent>(
  DashboardSnapshotReducer(),
) {

  val weightModelProducer = CartesianChartModelProducer()
  val bpModelProducer = CartesianChartModelProducer()
  val babyModelProducers = mutableMapOf<String, CartesianChartModelProducer>()
  private var weightGraphJob: Job? = null
  private var bpGraphJob: Job? = null

  override fun provideInitialState() = DashboardSnapshotState(isLoading = true)

  override fun onDependenciesReady() {
    observeWeightUnit()
    loadWeightGraph()
    loadBpGraph()
    loadBabyGraphs()
  }

  private fun observeWeightUnit() {
    viewModelScope.launch {
      accountService.activeAccountFlow
        .map { it?.weightUnit }
        .distinctUntilChanged()
        .collect { unit ->
          if (unit != null) {
            handleIntent(DashboardSnapshotIntent.SetWeightUnit(unit))
          }
        }
    }
  }

  private fun loadWeightGraph() {
    weightGraphJob?.cancel()
    weightGraphJob = viewModelScope.launch {
      entryReadService.snapshotFor(IEntryReadService.SNAPSHOT_WEIGHT)
        .collect { points ->
          updateWeightChart(points.filterIsInstance<WeightSnapshotPoint>())
          handleIntent(DashboardSnapshotIntent.SetLoading(false))
        }
    }
  }

  private suspend fun updateWeightChart(points: List<WeightSnapshotPoint>) {
    val entries = points.filter { it.weight > 0 }.sortedBy { it.entryTimestamp }
    if (entries.isEmpty()) {
      handleIntent(DashboardSnapshotIntent.SetWeightChart(SnapshotChartData(label = "—")))
      return
    }

    // DB stores weight in tenths — divide by 10 for display
    val displayWeights = entries.map { it.weight / 10.0 }
    val avgWeight = displayWeights.average()

    val xValues = entries.map { it.getTimeStamp().toDouble() }
    val yValues = displayWeights

    if (xValues.size == yValues.size && xValues.isNotEmpty()) {
      val graphMeta = generateNiceScale(
        minValue = yValues.min(),
        maxValue = yValues.max(),
        goalWeight = 0.0,
        targetTickCount = 4,
      )
      val endX = xValues.max().toLong()
      val startX = xValues.min().toLong()
      val startTimestamp = GraphUtil.getStartRange(segment = GraphSegment.WEEK, startX)
      val endTimestamp = GraphUtil.getRelativeEnd(segment = GraphSegment.WEEK, endX)

      handleIntent(
        DashboardSnapshotIntent.SetWeightChart(
          SnapshotChartData(
            label = formatWeightValue(avgWeight),
            yStep = graphMeta.step,
            yMin = graphMeta.min,
            yMax = graphMeta.max,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
          ),
        ),
      )

      try {
        weightModelProducer.runTransaction {
          lineSeries {
            series(x = xValues, y = yValues)
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to update weight chart model", e)
      }
    }
  }

  private fun loadBpGraph() {
    bpGraphJob?.cancel()
    bpGraphJob = viewModelScope.launch {
      entryReadService.snapshotFor(IEntryReadService.SNAPSHOT_BP)
        .collect { points ->
          updateBpChart(points.filterIsInstance<PeriodBpmSummary>())
        }
    }
  }

  private suspend fun updateBpChart(points: List<PeriodBpmSummary>) {
    if (points.isEmpty()) {
      handleIntent(DashboardSnapshotIntent.SetBpChart(SnapshotChartData(label = "—")))
      return
    }

    val sorted = points.sortedBy { it.entryTimestamp }
    val avgSystolic = sorted.map { it.avgSystolic }.average().toInt()
    val avgDiastolic = sorted.map { it.avgDiastolic }.average().toInt()
    val avgPulse = sorted.map { it.avgPulse }.average().toInt()

    val xValues = sorted.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp).toDouble() }
    val systolicValues = sorted.map { it.avgSystolic.toDouble() }
    val diastolicValues = sorted.map { it.avgDiastolic.toDouble() }
    val pulseValues = sorted.map { it.avgPulse.toDouble() }


    if (xValues.isNotEmpty()) {
      val allYValues = systolicValues + diastolicValues
      val graphMeta = generateNiceScale(
        minValue = allYValues.min(),
        maxValue = allYValues.max(),
        goalWeight = 0.0,
        targetTickCount = 4,
      )
      val endX = xValues.max().toLong()
      val startX = xValues.min().toLong()
      val startTimestamp = GraphUtil.getStartRange(segment = GraphSegment.WEEK, startX)
      val endTimestamp = GraphUtil.getRelativeEnd(segment = GraphSegment.WEEK, endX)

      handleIntent(
        DashboardSnapshotIntent.SetBpChart(
          SnapshotChartData(
            label = "$avgSystolic/$avgDiastolic",
            secondaryLabel = "$avgPulse",
            yStep = graphMeta.step,
            yMin = graphMeta.min,
            yMax = graphMeta.max,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
          ),
        ),
      )

      try {
        bpModelProducer.runTransaction {
          lineSeries {
            series(x = xValues, y = systolicValues)
            series(x = xValues, y = diastolicValues)
            series(x = xValues, y = pulseValues)
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to update BP chart model", e)
      }
    }
  }

  private fun loadBabyGraphs() {
    BabyPercentileHelper.loadIfNeeded(context)
    viewModelScope.launch {
      combine(
        productSelectionManager.availableProducts,
        entryReadService.babySnapshotsFlow(),
      ) { products, babyMap ->
        products.filterIsInstance<ProductSelection.Baby>() to babyMap
      }.collect { (babyProducts, babyMap) ->
        babyProducts.forEach { baby ->
          val points = babyMap[baby.profile.id] ?: emptyList()
          updateBabyChart(baby.profile, points)
        }
      }
    }
  }

  private suspend fun updateBabyChart(profile: BabyProfile, points: List<PeriodBabySummary>) {
    val profileId = profile.id
    val sorted = points.filter { (it.avgWeightDecigrams ?: 0) > 0 }.sortedBy { it.entryTimestamp }
    if (sorted.isEmpty()) {
      handleIntent(DashboardSnapshotIntent.SetBabyChart(profileId, SnapshotChartData(label = "—")))
      return
    }

    val xValues = sorted.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp).toDouble() }
    // Convert decigrams to lb + oz display
    val latestWeight = sorted.last().avgWeightDecigrams ?: 0
    val lbs = ConversionTools.convertDecigramsToLb(latestWeight)
    val oz = ConversionTools.convertDecigramsToOz(latestWeight)
    val label = "$lbs ${DashboardSnapshotStrings.Lbs} ${String.format("%.1f", oz)} ${DashboardSnapshotStrings.Oz}"

    // Convert decigrams to lbs for chart display
    val yValues = sorted.map { ConversionTools.convertDecigramsToLbExact(it.avgWeightDecigrams ?: 0) }

    if (xValues.isNotEmpty()) {
      val endX = xValues.max().toLong()
      val startX = xValues.min().toLong()
      val startTimestamp = GraphUtil.getStartRange(segment = GraphSegment.WEEK, startX)
      val endTimestamp = GraphUtil.getRelativeEnd(segment = GraphSegment.WEEK, endX)

      // Percentile curves from birth to age+120 days (dense, own X timestamps)
      val birthDateMillis = profile.birthdate?.let { DateTimeConverter.isoToTimestamp(it) }
      val pSeries = if (birthDateMillis != null) {
        BabyPercentileHelper.getPercentileSeries(
          sex = profile.sex,
          birthDateMillis = birthDateMillis,
        )
      } else null

      // Only include visible percentile values in Y range
      val visibleP5 = pSeries?.let { s ->
        s.p5.filterIndexed { i, _ -> s.xTimestamps[i] in xValues.min()..xValues.max() }
      } ?: emptyList()
      val visibleP95 = pSeries?.let { s ->
        s.p95.filterIndexed { i, _ -> s.xTimestamps[i] in xValues.min()..xValues.max() }
      } ?: emptyList()
      val allYValues = yValues + visibleP5 + visibleP95
      val graphMeta = generateNiceScale(
        minValue = allYValues.min(),
        maxValue = allYValues.max(),
        goalWeight = 0.0,
        targetTickCount = 4,
      )

      // Start from the week of the last percentile point before weight data
      val percentileWeekStart = pSeries?.xTimestamps
        ?.lastOrNull { it < (startTimestamp?.toDouble() ?: Double.MAX_VALUE) }
        ?.toLong()
        ?.let { GraphUtil.getStartRange(GraphSegment.WEEK, it) }

      handleIntent(
        DashboardSnapshotIntent.SetBabyChart(
          profileId,
          SnapshotChartData(
            label = label,
            yStep = graphMeta.step,
            yMin = graphMeta.min,
            yMax = graphMeta.max,
            startTimestamp = percentileWeekStart ?: startTimestamp,
            endTimestamp = endTimestamp,
            hasPercentile = pSeries != null,
          ),
        ),
      )

      val producer = babyModelProducers.getOrPut(profileId) { CartesianChartModelProducer() }
      try {
        producer.runTransaction {
          // Layer 1 (behind): percentile bands
          if (pSeries != null) {
            lineSeries {
              pSeries.allBands().forEach { band ->
                series(x = pSeries.xTimestamps, y = band)
              }
            }
          }
          // Layer 2 (on top): baby weight line
          lineSeries {
            series(x = xValues, y = yValues)
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to update baby chart model for $profileId", e)
      }
    }
  }

  fun getBabyModelProducer(profileId: String): CartesianChartModelProducer =
    babyModelProducers.getOrPut(profileId) { CartesianChartModelProducer() }

  companion object {
    private const val TAG = "DashboardSnapshotVM"
  }
}
