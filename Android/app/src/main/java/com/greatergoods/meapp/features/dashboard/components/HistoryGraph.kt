package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SegmentButtonGroup
import com.greatergoods.meapp.features.common.components.chart.GraphView
import com.greatergoods.meapp.features.common.enum.GraphSegment
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

@Composable
fun HistoryGraph(state: DashboardState) {
    var selectedSegment by remember { mutableStateOf(GraphSegment.WEEK) }
    var graphLines by remember {
        mutableStateOf(state.dayWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints())
    }

    Column(
        modifier =
            Modifier
                .background(MeTheme.colorScheme.primaryBackground),
    ) {
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
        Text(
            text =
                buildAnnotatedString {
                    append("000.0")
                    withStyle(
                        style =
                            SpanStyle(
                                fontSize = MeTheme.typography.subHeading2.fontSize,
                                color = MeTheme.colorScheme.textSubheading,
                            ),
                    ) {
                        append(" lbs")
                    }
                },
            modifier = Modifier.padding(
                horizontal = MeTheme.spacing.sm,
            ),
            style = MeTheme.typography.heading1,
            color = MeTheme.colorScheme.textBody,
        )
        GraphView(
            modifier =
                Modifier
                    .fillMaxHeight(0.5f)
                    .fillMaxWidth(),
            segment = selectedSegment,
            graphLines = listOf(graphLines),
            selectedData = null,
            onSelected = {
            },
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
        SegmentButtonGroup(
            data = GraphSegment.entries.toList(),
            selectedData = selectedSegment,
            key = GraphSegment::name,
            onSelected = { segment ->
                selectedSegment = segment
                graphLines =
                    when (segment) {
                        GraphSegment.YEAR, GraphSegment.TOTAL -> {
                            state.monthWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
                        }

                        GraphSegment.MONTH, GraphSegment.WEEK -> {
                            state.dayWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
                        }
                    }
            },
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm)
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
        )
    }
}
