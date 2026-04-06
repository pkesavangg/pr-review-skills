package com.dmdbrands.gurus.weight.features.dashboard.snapshot.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import android.content.Context
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardSnapshotViewModel @Inject constructor(
  @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
  private val historyService: IHistoryService,
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
      historyService.getWeightSnapshotGraphData()
        .catch { e ->
          AppLog.e(TAG, "Failed to load weight graph data", e)
          handleIntent(DashboardSnapshotIntent.SetLoading(false))
        }
        .collect { points ->
          updateWeightChart(points)
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
      historyService.getBpmSnapshotGraphData()
        .catch { e ->
          AppLog.e(TAG, "Failed to load BP graph data", e)
        }
        .collect { points ->
          updateBpChart(points)
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
      productSelectionManager.availableProducts.collect { products ->
        products.filterIsInstance<ProductSelection.Baby>().forEach { baby ->
          loadBabyGraph(baby.profile)
        }
      }
    }
  }

  private fun loadBabyGraph(profile: BabyProfile) {
    viewModelScope.launch {
      historyService.getBabySnapshotGraphData(profile.id)
        .catch { e ->
          AppLog.e(TAG, "Failed to load baby graph data for ${profile.id}", e)
        }
        .collect { points ->
          updateBabyChart(profile, points)
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
    val label = "$lbs lbs ${String.format("%.1f", oz)} oz"

    // Convert decigrams to lbs for chart display (decigrams / 283.495 / 16)
    val yValues = sorted.map { (it.avgWeightDecigrams ?: 0) / 283.495 / 16.0 }

    if (xValues.isNotEmpty()) {
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
        DashboardSnapshotIntent.SetBabyChart(
          profileId,
          SnapshotChartData(
            label = label,
            yStep = graphMeta.step,
            yMin = graphMeta.min,
            yMax = graphMeta.max,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
          ),
        ),
      )

      // Compute percentile lines (p5, p50, p95) for the chart X range
      val birthDate = profile.birthDate
      val daysSinceBirth = if (birthDate != null) {
        xValues.map { x ->
          ((x.toLong() - birthDate) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        }
      } else null

      val percentileLines = if (daysSinceBirth != null) {
        BabyPercentileHelper.getPercentileLines(profile.biologicalSex, daysSinceBirth)
      } else emptyMap()

      val producer = babyModelProducers.getOrPut(profileId) { CartesianChartModelProducer() }
      try {
        // Convert percentile decigrams to lbs
        val p5 = percentileLines["p5"]?.map { it / 283.495 / 16.0 }
        val p50 = percentileLines["p50"]?.map { it / 283.495 / 16.0 }
        val p95 = percentileLines["p95"]?.map { it / 283.495 / 16.0 }

        producer.runTransaction {
          lineSeries {
            series(x = xValues, y = yValues)
            if (p50 != null && p50.size == xValues.size) {
              if (p95 != null) series(x = xValues, y = p95)
              series(x = xValues, y = p50)
              if (p5 != null) series(x = xValues, y = p5)
            }
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
