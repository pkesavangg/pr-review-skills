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
import androidx.compose.runtime.LaunchedEffect
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
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.dashboard.viewmodel.DashboardState
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.util.Log

@Composable
fun HistoryGraph(state: DashboardState) {
    if (state.isLoading) {
        Text(
            text = "Loading...",
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
            style = MeTheme.typography.body2,
            color = MeTheme.colorScheme.textSubheading,
        )
        return
    }
    var selectedSegment by remember { mutableStateOf(GraphSegment.WEEK) }

    var subText : String?  by remember { mutableStateOf(null) }

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

    var graphLines by remember(state.dayWiseEntries, state.monthWiseEntries) {
        mutableStateOf(getWeightGraphPoints(selectedSegment))
    }
    var selectedData by remember(graphLines) {
        mutableStateOf(
            graphLines.points.lastOrNull()?.let { listOf(it) } ?: emptyList(),
        )
    }

    val labelData =
        if (selectedData.isNotEmpty()) selectedData.first().y.value.toDouble().rounded().toString() else "000"
    Column(
        modifier =
            Modifier
                .background(MeTheme.colorScheme.primaryBackground),
    ) {

        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))

        Text(
            text =
                buildAnnotatedString {
                    append(labelData)
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
        Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
        subText?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
                style = MeTheme.typography.body2,
                color = MeTheme.colorScheme.textSubheading,
            )
        }
        GraphView(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f),
            segment = selectedSegment,
            graphLines = listOf(graphLines),
            selectedData = selectedData,
            onScroll = {
                subText = it
            },
            onSelected = {
                selectedData = it
            },
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.lg))
        SegmentButtonGroup(
            data = GraphSegment.entries.toList(),
            selectedData = selectedSegment,
            key = GraphSegment::name,
            onSelected = { segment ->
                selectedSegment = segment
                graphLines = getWeightGraphPoints(segment)

            },
            modifier = Modifier.padding(horizontal = MeTheme.spacing.sm),
        )
        Spacer(modifier = Modifier.height(MeTheme.spacing.sm))

    }

    LaunchedEffect(graphLines) {
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
