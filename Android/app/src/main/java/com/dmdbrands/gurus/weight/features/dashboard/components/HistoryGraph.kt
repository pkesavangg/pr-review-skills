package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphPagerView
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.theme.MeAppTheme

@Composable
fun HistoryGraph(
  state: DashboardState,
  selectedSegment: GraphSegment = GraphSegment.WEEK,
  selectedStat: Stat? = null,
  onSelectSegment: (GraphSegment) -> Unit = {},
  onSelected: (List<PeriodBodyScaleSummary>) -> Unit,
  onPagerStateChange: (Int) -> Unit = {}
) {
  GraphPagerView(
    state = state,
    selectedStat = selectedStat,
    onSelected = onSelected,
    onPagerStateChange = onPagerStateChange,
    onSegmentChange = onSelectSegment
  )
}

@PreviewTheme
@Composable
private fun HistoryGraphPreview() {
  MeAppTheme {
    HistoryGraph(
      state = DashboardState(),
      onSelected = {}
    )
  }
}
