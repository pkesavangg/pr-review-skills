package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.BpGraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ──

@Stable
data class BpDashboardState(
  // Base graph state
  override val dailyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val monthlyProducer: CartesianChartModelProducer = CartesianChartModelProducer(),
  override val segmentStates: Map<GraphSegment, SegmentState> = emptyMap(),
  override val selectedSegment: GraphSegment = GraphSegment.WEEK,
  override val scrollTarget: Double? = null,
  override val isRefreshing: Boolean = false,
  override val weightUnit: WeightUnit = WeightUnit.KG,
  override val goal: Goal? = null,
  // BP-specific content
  val progress: Progress = Progress(),
  val isEmpty: Boolean = false,
) : BaseDashboardState

// ── Intents ──

sealed interface BpDashboardIntent : IReducer.Intent {
  data class UpdateSegment(val segment: GraphSegment, val update: (SegmentState) -> SegmentState) : BpDashboardIntent
  data class SetProducers(val daily: CartesianChartModelProducer, val monthly: CartesianChartModelProducer) : BpDashboardIntent
  data class SetRefreshing(val isRefreshing: Boolean) : BpDashboardIntent
  data class SetSelectedSegment(val segment: GraphSegment) : BpDashboardIntent
  data class SetProgress(val progress: Progress) : BpDashboardIntent
  data class SetIsEmpty(val isEmpty: Boolean) : BpDashboardIntent
}

// ── Reducer ──

class BpDashboardReducer : IReducer<BpDashboardState, BpDashboardIntent> {
  override fun reduce(state: BpDashboardState, intent: BpDashboardIntent): BpDashboardState? = when (intent) {
    is BpDashboardIntent.UpdateSegment -> {
      val current = state.segmentStates[intent.segment] ?: SegmentState()
      state.copy(segmentStates = state.segmentStates + (intent.segment to intent.update(current)))
    }
    is BpDashboardIntent.SetProducers -> state.copy(dailyProducer = intent.daily, monthlyProducer = intent.monthly)
    is BpDashboardIntent.SetRefreshing -> state.copy(isRefreshing = intent.isRefreshing)
    is BpDashboardIntent.SetSelectedSegment -> state.copy(selectedSegment = intent.segment)
    is BpDashboardIntent.SetProgress -> state.copy(progress = intent.progress)
    is BpDashboardIntent.SetIsEmpty -> state.copy(isEmpty = intent.isEmpty)
  }
}

// ── ViewModel ──

@HiltViewModel
class BpDashboardViewModel @Inject constructor(
  private val historyService: IHistoryService,
  private val entryService: IEntryService,
  private val accountService: IAccountService,
) : BaseDashboardViewModel<BpDashboardState, BpDashboardIntent>(
  reducer = BpDashboardReducer(),
) {

  companion object {
    private const val TAG = "BpDashboardVM"
  }

  override val adapter: GraphDataAdapter = BpGraphDataAdapter()

  override fun getDailyDataFlow(): Flow<GraphData> =
    historyService.getDailyGraphData(ProductSelection.BloodPressure)

  override fun getMonthlyDataFlow(): Flow<GraphData> =
    historyService.getMonthlyGraphData(ProductSelection.BloodPressure)

  override fun updateSegmentState(segment: GraphSegment, update: (SegmentState) -> SegmentState) {
    handleIntent(BpDashboardIntent.UpdateSegment(segment, update))
  }

  override fun setProducers(daily: CartesianChartModelProducer, monthly: CartesianChartModelProducer) {
    handleIntent(BpDashboardIntent.SetProducers(daily, monthly))
  }

  override fun setRefreshing(isRefreshing: Boolean) {
    handleIntent(BpDashboardIntent.SetRefreshing(isRefreshing))
  }

  override fun provideInitialState(): BpDashboardState = BpDashboardState()

  override fun onDependenciesReady() {
    startGraphSubscriptions()
    subscribeProgress()
  }

  fun refresh() {
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
