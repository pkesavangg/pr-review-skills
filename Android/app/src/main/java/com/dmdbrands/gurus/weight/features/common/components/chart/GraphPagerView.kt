package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.formatWeightValue
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog

/**
 * Composable for displaying a horizontal pager with 4 graph views for different segments.
 * Each page represents a different time segment (WEEK, MONTH, YEAR, TOTAL).
 *
 * @param state The dashboard state containing data and configuration.
 * @param onSelected Callback when entries are selected from the graph.
 * @param onPagerStateChange Callback when pager state changes.
 * @param onSegmentChange Callback when segment is selected.
 * @param onRangeChange Callback for date range label updates.
 * @param onMarkerIndexChange Callback when marker selection changes.
 * @param entries List of entries to be used by the graph viewmodels.
 */
@Composable
fun GraphPagerView(
  state: DashboardState,
  onSelected: (List<PeriodBodyScaleSummary>) -> Unit,
  onPagerStateChange: (Int) -> Unit,
  onSegmentChange: (GraphSegment) -> Unit = {},
  onChartConsuming: (Boolean) -> Unit = {},
  onRangeChange: (String) -> Unit = { },
  onMarkerIndexChange: (Double?) -> Unit = {},
  onLatestDaySelectedChange: (Boolean) -> Unit = {},
  entries: List<PeriodBodyScaleSummary> = emptyList()
) {
  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  var subText: String by remember { mutableStateOf("") }
  var labelData by remember { mutableStateOf("") }
  var weightValue by remember { mutableStateOf(0.0) }

  // One-shot signal emitted on every segment-button tap. Each GraphView page subscribes
  // and the page whose segment matches the emitted value re-arms its scroll-to-initial.
  // Using an explicit user-action signal (rather than inferring from `isCurrentPage`
  // transitions) avoids both rotation/resume false-positives and pager-timing false-
  // negatives where `pagerState.currentPage` updates before a freshly composed page
  // observes the transition.
  // replay = 1: new subscribers (freshly composed pages) immediately receive the last-emitted
  // segment even if they subscribed after the emission. `remember { }` recreates this flow on
  // rotation / navigation-stack push, so the replay cache is always session-scoped — no
  // spurious resets after a config change.
  val segmentResetSignal = remember { MutableSharedFlow<GraphSegment>(replay = 1, extraBufferCapacity = 4) }

  LaunchedEffect(state.selectedSegment) {
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) {
      pagerState.scrollToPage(targetPage)
    }
  }

  // Handle pager page changes
  LaunchedEffect(pagerState.currentPage) {
    onPagerStateChange(pagerState.currentPage)
  }

  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {

    HorizontalPager(
      state = pagerState,
      userScrollEnabled = false,
      modifier = Modifier.fillMaxWidth(),
    ) { page ->
      val currentSegment = GraphSegment.entries.getOrNull(page) ?: GraphSegment.WEEK
      val viewmodel = hiltViewModel<GraphViewModel, GraphViewModel.Factory>(key = "GraphViewModel-$page") { factory ->
        factory.create(currentSegment)
      }
      val graphState by viewmodel.state.collectAsState()

      LaunchedEffect(graphState.target) {
        val averageWeight = if (graphState.target.isEmpty()) 0.0 else graphState.target.map { it.weight }.average()
        labelData = if (graphState.target.isEmpty()) "000.0" else formatWeightValue(averageWeight)
        if (averageWeight > 0 && state.weightless?.isWeightlessOn == true) {
          labelData = ("+").plus(labelData)
        }
        weightValue = averageWeight
        onSelected(graphState.target)
      }

      LaunchedEffect(graphState.markerIndex) {
        onMarkerIndexChange(graphState.markerIndex)
      }

      // Per MA-3965: report whether the currently selected graph point lands on the
      // most recent day in the data set, so the dashboard can route the metric-info
      // sheet's label between "latest entry" and "day average".
      LaunchedEffect(graphState.markerIndex, graphState.data) {
        val marker = graphState.markerIndex?.toLong()
        val latestDay = graphState.data.maxOfOrNull { it.getTimeStamp() }
        onLatestDaySelectedChange(marker != null && latestDay != null && marker == latestDay)
      }

      LaunchedEffect(graphState.minTarget, graphState.maxTarget, pagerState.currentPage, state.isConsuming) {
        if (graphState.minTarget != null && graphState.maxTarget != null && !state.isConsuming) {
          val (minTarget, maxTarget) = if (currentSegment == GraphSegment.TOTAL && !graphState.isEmptyGraph) {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = graphState.minTarget!!
            calendar.add(java.util.Calendar.MONTH, +6)
            val min = calendar.timeInMillis

            val calendar1 = java.util.Calendar.getInstance()
            calendar1.timeInMillis = graphState.maxTarget!!
            calendar1.add(java.util.Calendar.MONTH, -6)
            val max = calendar1.timeInMillis
            min to max
          } else {
            graphState.minTarget!! to graphState.maxTarget!!
          }
          val formattedRange = GraphUtil.formatDateRange(minTarget, maxTarget, currentSegment)
          AppLog.i(
            "GraphView",
            "segment : ${currentSegment} minTarget : ${minTarget} maxTarget : ${maxTarget} formattedRange : ${formattedRange}",
          )
          subText = formattedRange
          onRangeChange(formattedRange)
        }
      }
      Column {
        ChartHeader(
          state = graphState,
          segment = currentSegment,
          weightData = labelData,
          rangeData = subText,
          weightValue = weightValue,
        )

        GraphView(
          modifier = Modifier.fillMaxWidth(),
          segment = currentSegment,
          isCurrentPage = pagerState.currentPage == page,
          state = graphState,
          viewModel = viewmodel,
          onChartConsuming = onChartConsuming,
          segmentResetSignal = segmentResetSignal,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      }
    }

    SegmentButtonGroup(
      data = GraphSegment.entries.toList(),
      selectedData = GraphSegment.entries[pagerState.currentPage],
      key = GraphSegment::name,
      onSelected = { segment ->
        onChartConsuming(true)
        onSegmentChange(segment)
        // Explicit reset-to-initial signal — only the page whose segment matches consumes
        // it. Fires on every tap (including same-segment taps), which matches the user
        // expectation that pressing a segment button shows that segment from its initial
        // window. Rotation, history-screen return, and app resume do NOT emit, so scroll
        // is preserved across non-tap recompositions.
        segmentResetSignal.tryEmit(segment)
      },
      modifier = Modifier.padding(horizontal = MeTheme.spacing.xs),
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}

@PreviewTheme
@Composable
private fun GraphPagerViewPreview() {
  MeAppTheme {
    GraphPagerView(
      state = DashboardState(),
      onSelected = {},
      onPagerStateChange = {},
    )
  }
}
