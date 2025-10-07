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
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
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
  selectedStat: Stat? = null,
  onSelected: (List<PeriodBodyScaleSummary>) -> Unit,
  onPagerStateChange: (Int) -> Unit,
  onSegmentChange: (GraphSegment) -> Unit = {},
  onScrollTargetChange: (Double?) -> Unit = {},
  entries: List<PeriodBodyScaleSummary> = emptyList()
) {
  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment).takeIf { it >= 0 } ?: 0,
    pageCount = { GraphSegment.entries.size },
  )

  var scrollTarget: Double? by remember {
    mutableStateOf(null)
  }

  LaunchedEffect(state.selectedSegment) {
    onScrollTargetChange(scrollTarget)
  }

  var subText: String by remember { mutableStateOf("") }
  var labelData by remember { mutableStateOf("") }

  LaunchedEffect(state.selectedSegment) {
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

  val validMetricKey = if (selectedStat?.key is DashboardKey.Metric) {
    selectedStat.key.key
  } else null
  // Cache data processing to avoid repeated calculations
  val segmentEntries = remember(state.selectedSegment) {
    getEntriesForSegment(state)
  }
  val segmentGraphLines = remember(state.selectedSegment) {
    getWeightGraphPointsForSegment(state)
  }
  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {

    HorizontalPager(
      state = pagerState,
      userScrollEnabled = false,
      modifier = Modifier.fillMaxWidth(),
    ) { page ->

      val viewmodel = hiltViewModel<GraphViewModel, GraphViewModel.Factory>(key = "GraphViewModel-$page") { factory ->
        factory.create(segment = state.selectedSegment)
      }
      val graphState by viewmodel.state.collectAsState()

      Column {
        // Header section for current segment
        ChartHeader(
          state = graphState,
          segment = state.selectedSegment,
          weightData = labelData,
          rangeData = subText,
        )
        // Graph view with crossfade animation

        GraphView(
          modifier = Modifier
            .fillMaxWidth(),
          scrollTarget = state.scrollTarget,
          secondaryGraphLines = validMetricKey?.let { segmentEntries.toGraphPoints(validMetricKey) },
          graphLines = listOf(segmentGraphLines),
          segment = state.selectedSegment,
          state = graphState,
          onRangeUpdate = {
            if (it != null) {
              subText = it
            }
          },
          onTargetsUpdate = { targets, fallbackValue ->
            val timeStamps = targets.map { it.toLong() }
            val filteredEntries = segmentEntries.filter {
              DateTimeConverter.isoToTimestamp(it.entryTimestamp) in timeStamps
            }
            onSelected(filteredEntries)
            scrollTarget = if (targets.isNotEmpty())
              targets.last()
            else
              null
          },
          onWeightLabelUpdate = { label ->
            labelData = label
          },
          viewModel = viewmodel,
        )
      }
    }

    // Segment button group
    SegmentButtonGroup(
      data = GraphSegment.entries.toList(),
      selectedData = state.selectedSegment,
      key = GraphSegment::name,
      onSelected = { segment ->
        onSegmentChange(segment)
      },
      modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
    )

    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))
  }
}

/**
 * Gets the appropriate entries for the given segment.
 */
private fun getEntriesForSegment(
  state: DashboardState,
): List<PeriodBodyScaleSummary> {
  return when (state.selectedSegment) {
    GraphSegment.YEAR, GraphSegment.TOTAL -> state.monthWiseEntries
    GraphSegment.MONTH, GraphSegment.WEEK -> state.dayWiseEntries
  }
}

/**
 * Gets the weight graph points for the given segment.
 * Optimized to avoid repeated sorting and processing.
 */
private fun getWeightGraphPointsForSegment(
  state: DashboardState,
): GraphLine {
  val entries = getEntriesForSegment(state)
  // Sort only if entries are not already sorted
  val sortedEntries = if (entries.size <= 1) {
    entries
  } else {
    // Check if already sorted to avoid unnecessary sorting
    val isSorted = entries.zipWithNext().all { (a, b) -> a.entryTimestamp <= b.entryTimestamp }
    if (isSorted) entries else entries.sortedBy { it.entryTimestamp }
  }
  return sortedEntries.toWeightGraphPoints()
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
