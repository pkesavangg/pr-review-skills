package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
  entries: List<PeriodBodyScaleSummary> = emptyList()
) {
  val pagerState = rememberPagerState(
    initialPage = GraphSegment.entries.indexOf(state.selectedSegment),
    pageCount = { GraphSegment.entries.size },
  )

  var subText: String? by remember { mutableStateOf(null) }
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

  Column(
    modifier = Modifier.background(MeTheme.colorScheme.primaryBackground),
  ) {
    // Header section with current segment info
    Column(
      modifier = Modifier.padding(
        horizontal = MeTheme.spacing.sm,
        vertical = MeTheme.spacing.x3s,
      ),
    ) {
      Text(
        text = "${state.selectedSegment.name.lowercase()} average",
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )

      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = labelData.ifBlank { "000.0" },
          style = MeTheme.typography.heading2,
          color = MeTheme.colorScheme.textBody,
        )

        val weightUnit = getWeightUnitForSegment(state, state.selectedSegment)
        if (labelData.isNotBlank() && weightUnit != null) {
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = weightUnit.name.lowercase(),
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.offset(y = (-10).dp),
          )
        }
      }

      Box(contentAlignment = Alignment.TopStart) {
        Text(
          text = subText ?: "No data available",
          style = MeTheme.typography.subHeading2,
          color = if (subText != null) MeTheme.colorScheme.textSubheading else Color.Transparent,
        )
      }
    }

    // Horizontal pager with graph views
    if (state.dayWiseEntries.isEmpty()) {
      EmptyGraph(state.selectedSegment)
    } else {

      HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth(),
      ) { page ->
        val currentSegment = GraphSegment.entries[page]
        val segmentEntries = getEntriesForSegment(state, currentSegment)
        val segmentGraphLines = getWeightGraphPointsForSegment(state, currentSegment)
        val viewmodel = hiltViewModel<GraphViewModel, GraphViewModel.Factory>(key = "GraphViewModel-$page") { factory ->
          factory.create(GraphSegment.entries[page])
        }

        GraphView(
          modifier = Modifier
            .fillMaxWidth()
            .let {
              if (currentSegment == GraphSegment.TOTAL) {
                it.padding(start = 16.dp)
              } else {
                it
              }
            },
          secondaryGraphLines = validMetricKey?.let { segmentEntries.toGraphPoints(validMetricKey) },
          graphLines = listOf(segmentGraphLines),
          segment = currentSegment,
          goal = state.goal,
          onRangeUpdate = { subText = it },
          onTargetsUpdate = { targets, fallbackValue ->
            val timeStamps = targets.map { it.toLong() }
            val filteredEntries = segmentEntries.filter {
              DateTimeConverter.isoToTimestamp(it.entryTimestamp) in timeStamps
            }
            onSelected(filteredEntries)
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
  segment: GraphSegment
): List<PeriodBodyScaleSummary> {
  return when (segment) {
    GraphSegment.YEAR, GraphSegment.TOTAL -> state.monthWiseEntries
    GraphSegment.MONTH, GraphSegment.WEEK -> state.dayWiseEntries
  }
}

/**
 * Gets the weight graph points for the given segment.
 */
private fun getWeightGraphPointsForSegment(
  state: DashboardState,
  segment: GraphSegment
): GraphLine {
  val entries = getEntriesForSegment(state, segment)
  return entries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
}

/**
 * Gets the weight unit for the current segment.
 */
private fun getWeightUnitForSegment(
  state: DashboardState,
  segment: GraphSegment
): com.dmdbrands.gurus.weight.domain.model.common.WeightUnit? {
  val entries = getEntriesForSegment(state, segment)
  return if (entries.isNotEmpty()) entries.random().unit else null
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
