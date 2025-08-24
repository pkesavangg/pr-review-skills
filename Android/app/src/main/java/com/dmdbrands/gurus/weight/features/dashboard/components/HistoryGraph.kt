package com.dmdbrands.gurus.weight.features.dashboard.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.chart.GraphView
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toGraphPoints
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.DashboardState
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

@Composable
fun HistoryGraph(
  state: DashboardState,
  selectedSegment: GraphSegment = GraphSegment.WEEK,
  selectedStat: Stat? = null,
  onSelectSegment: (GraphSegment) -> Unit = {},
  onSelected: (List<PeriodBodyScaleSummary>) -> Unit
) {

  var subText: String? by remember { mutableStateOf(null) }

  fun getWeightGraphPoints(segment: GraphSegment): GraphLine {
    return when (segment) {
      GraphSegment.YEAR, GraphSegment.TOTAL -> {
        state.monthWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
      }

      GraphSegment.MONTH, GraphSegment.WEEK -> {
        state.dayWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
      }
    }
  }

  var entries by remember(state.dayWiseEntries, state.monthWiseEntries) {
    mutableStateOf(state.dayWiseEntries)
  }

  var graphLines by remember(entries) {
    mutableStateOf(getWeightGraphPoints(selectedSegment))
  }

  var labelData by remember {
    mutableStateOf("")
  }

  val validMetricKey = if (selectedStat?.key is DashboardKey.Metric) {
    selectedStat.key.key
  } else null

  val weightUnit = if (entries.isNotEmpty()) entries.random().unit else null
  buildAnnotatedString {
    withStyle(style = MeTheme.typography.heading2.toSpanStyle()) {
      append(labelData.ifBlank { "---" })
    }

    if (labelData.isNotBlank() && weightUnit != null) {

      withStyle(
        style = MeTheme.typography.subHeading2.toSpanStyle().copy(
          baselineShift = BaselineShift(0.05f), // subtle subscript
          color = MeTheme.colorScheme.textBody,
        ),
      ) {
        append(weightUnit.label)
      }
    }
  }


  Column(
    modifier =
      Modifier
        .background(MeTheme.colorScheme.primaryBackground),
  ) {

    Column(modifier = Modifier.padding(horizontal = MeTheme.spacing.sm, vertical = MeTheme.spacing.x3s)) {
      Text(
        text = selectedSegment.name.lowercase().plus(" average"),
        style = MeTheme.typography.subHeading1,
        color = MeTheme.colorScheme.textSubheading,
      )
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = labelData.ifBlank { "---" },
          style = MeTheme.typography.heading2,
          color = MeTheme.colorScheme.textBody,
        )

        if (labelData.isNotBlank() && weightUnit != null) {
          Spacer(modifier = Modifier.width(4.dp))

          Text(
            text = weightUnit.label, // or dynamic unit
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
            modifier = Modifier.offset(y = (-10).dp), // shifts it slightly down like a subscript
          )
        }
      }

      Box(
        modifier = Modifier
          .height(18.dp),
        contentAlignment = Alignment.TopStart,
      ) {
        if (subText != null) {
          Text(
            text = subText!!,
            style = MeTheme.typography.subHeading2,
            color = MeTheme.colorScheme.textSubheading,
          )
        }
      }
    }
    GraphView(
      modifier =
        Modifier
          .fillMaxWidth(),
      segment = selectedSegment,
      secondaryGraphLines = validMetricKey?.let { entries.toGraphPoints(validMetricKey) },
      graphLines = listOf(graphLines),
      goal = state.goal,
      onScroll = {
        subText = it
      },
      onMetricUpdate = { grahpoints ->
        val timeStamps = grahpoints.map { it.x.value.toLong() }
        val filteredEntries =
          entries.filter { DateTimeConverter.isoToTimestamp(it.entryTimestamp) in timeStamps }
        onSelected(
          filteredEntries,
        )
      },
      onLabelUpdate = {
        labelData = it
      },
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
    SegmentButtonGroup(
      data = GraphSegment.entries.toList(),
      selectedData = selectedSegment,
      key = GraphSegment::name,
      onSelected = { segment ->
        onSelectSegment(segment)
        entries = when (segment) {
          GraphSegment.YEAR, GraphSegment.TOTAL -> state.monthWiseEntries
          GraphSegment.MONTH, GraphSegment.WEEK -> state.dayWiseEntries
        }
        graphLines = getWeightGraphPoints(segment)

      },
      modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
    )
    Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

  }
}

@PreviewTheme
@Composable
private fun HistoryGraphPreview() {
  MeAppTheme {
    HistoryGraph(
      state =
        DashboardState(),
    ) {}
  }
}
