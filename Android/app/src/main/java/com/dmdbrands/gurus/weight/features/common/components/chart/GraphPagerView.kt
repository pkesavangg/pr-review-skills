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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Composable for displaying a horizontal pager with 4 graph views for different segments.
 * Each page represents a different time segment (WEEK, MONTH, YEAR, TOTAL).
 *
 * @param state The dashboard state containing data and configuration.
 * @param selectedStat Optional selected stat for secondary graph lines.
 * @param onSelected Callback when entries are selected from the graph.
 * @param onPagerStateChange Callback when pager state changes.
 * @param entries List of entries to be used by the graph viewmodels.
 */
@Composable
fun GraphPagerView(
  state: DashboardState,
  onSelected: (List<PeriodBodyScaleSummary>) -> Unit,
  onPagerStateChange: (Int) -> Unit,
  onSegmentChange: (GraphSegment) -> Unit = {},
  onScrollTargetChange: (Double?) -> Unit = {},
  onRangeChange: (String) -> Unit = { },
  onMarkerIndexChange: (Double?) -> Unit = {},
  entries: List<PeriodBodyScaleSummary> = emptyList()
) {
  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  var scrollTarget: Double? by remember {
    mutableStateOf(null)
  }
  var subText: String by remember { mutableStateOf("") }
  var labelData by remember { mutableStateOf("") }
  var weightValue by remember { mutableStateOf(0.0) }

  LaunchedEffect(state.selectedSegment) {
    onScrollTargetChange(scrollTarget)
    val targetPage = GraphSegment.entries.indexOf(state.selectedSegment)
    if (targetPage != pagerState.currentPage) {
      // Update page directly
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
        labelData = if (graphState.target.isEmpty()) "000.0" else String.format(
          "%.2f",
          averageWeight,
        )
        weightValue = averageWeight
        scrollTarget =
          if (state.data.isNotEmpty()) DateTimeConverter.isoToTimestamp(state.data.last().entryTimestamp)
            .toDouble() else null
        onSelected(graphState.target)
      }


      LaunchedEffect(graphState.minTarget, graphState.maxTarget) {
        if (graphState.minTarget != null && graphState.maxTarget != null) {
          val (minTarget, maxTarget) = if (currentSegment == GraphSegment.TOTAL) {
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
          subText = formattedRange
          onRangeChange(formattedRange)
        }
      }
      Column {
        // Header section for current segment
        ChartHeader(
          state = graphState,
          segment = currentSegment,
          weightData = labelData,
          rangeData = subText,
          weightValue = weightValue,
        )
        // Graph view with crossfade animation

        GraphView(
          modifier = Modifier
            .fillMaxWidth(),
          scrollTarget = state.scrollTarget,
          segment = currentSegment,
          state = graphState,
          viewModel = viewmodel,
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
      }
    }

    // Segment button group
    SegmentButtonGroup(
      data = GraphSegment.entries.toList(),
      selectedData = GraphSegment.entries[pagerState.currentPage],
      key = GraphSegment::name,
      onSelected = { segment ->
        onSegmentChange(segment)
      },
      modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
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
