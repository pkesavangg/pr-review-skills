package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.BpGraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphDataAdapter
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardViewModel
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BpDashboardViewModel @Inject constructor(
  private val historyService: IHistoryService,
  private val entryService: IEntryService,
) : BaseDashboardViewModel<BpDashboardState, BaseGraphIntent>(
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

  override fun provideInitialState(): BpDashboardState = BpDashboardState()

  override fun onDependenciesReady() {
    startGraphSubscriptions()
    subscribeProgress()
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
      entryService.progress.collect { handleIntent(BpDashboardIntent.SetProgress(it)) }
    }
  }
}
