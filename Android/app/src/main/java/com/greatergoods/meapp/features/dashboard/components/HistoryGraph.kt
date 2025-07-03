package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.chart.GraphView
import com.greatergoods.meapp.features.common.enums.GraphSegment
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.toGraphPoints
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.greatergoods.meapp.features.common.model.DashboardKey
import com.greatergoods.meapp.features.common.model.Stat
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun HistoryGraph(
    state: DashboardState,
    selectedStat: Stat? = null,
    onSelected: (List<PeriodBodyScaleSummary>) -> Unit
) {
    var selectedSegment by remember { mutableStateOf(GraphSegment.WEEK) }

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
            Text(
                text = labelData.ifBlank { "---" },
                style = MeTheme.typography.heading2,
                lineHeight = 0.sp,
                color = MeTheme.colorScheme.textBody,

                )
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
                selectedSegment = segment
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
