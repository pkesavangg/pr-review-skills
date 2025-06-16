package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.chart.GraphView
import com.greatergoods.meapp.features.common.enum.GraphSegment
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.greatergoods.meapp.features.dashboard.DashBoardViewmodel
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun HistoryGraph() {
    val dashedBoardViewModel = hiltViewModel<DashBoardViewmodel>()
    val dashBoardState by dashedBoardViewModel.state.collectAsState()
    var selectedSegment by remember { mutableStateOf(GraphSegment.WEEK) }
    var isAddEntryModalVisible by remember { mutableStateOf(false) }
    var graphLines by remember {
        mutableStateOf(dashBoardState.dayWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints())
    }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .background(MeAppTheme.colorScheme.primary),
    ) {
        GraphView(
            modifier = Modifier
                .fillMaxHeight(0.5f)
                .fillMaxWidth(),
            segment = selectedSegment,
            graphLines = listOf(graphLines),
            selectedData = null,
            labelContent = {
                Text(
                    text = buildAnnotatedString {
                        append("000.0")
                        withStyle(
                            style = SpanStyle(
                                fontSize = MeAppTheme.typography.subHeading2.fontSize,
                                color = MeAppTheme.colorScheme.subheading,
                            ),
                        ) {
                            append(" lbs")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MeAppTheme.typography.heading1,
                    color = MeAppTheme.colorScheme.body,
                )
            },
            onSelected = {

            },
        )
        Spacer(modifier = Modifier.height(MeAppTheme.spacing.lg))
        GraphSegmentControl(
            selected = selectedSegment,
            onSelect = { segment ->
                selectedSegment = segment
                graphLines = when (segment) {
                    GraphSegment.YEAR, GraphSegment.TOTAL -> {
                        dashBoardState.monthWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
                    }

                    GraphSegment.MONTH, GraphSegment.WEEK -> {
                        dashBoardState.dayWiseEntries.sortedBy { it.entryTimestamp }.toWeightGraphPoints()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = MeAppTheme.spacing.md),
        )
        Button(
            onClick = { isAddEntryModalVisible = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = MeAppTheme.spacing.md),
        ) {
            Text("Add Entry")
        }
    }
    if (isAddEntryModalVisible) {
        AddScaleEntriesModal(
            onDismiss = { isAddEntryModalVisible = false },
            onEntriesGenerated = { entries ->
                if (entries.isNotEmpty()) {
                    dashedBoardViewModel.addEntry(entries)
                }
            },
        )
    }
}
