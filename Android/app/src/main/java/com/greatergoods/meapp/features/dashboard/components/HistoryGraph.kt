package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.features.common.components.chart.GraphView
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.dashboard.DashBoardViewmodel
import com.greatergoods.meapp.theme.MeAppTheme
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun HistoryGraph() {
    val dashedBoardViewModel = hiltViewModel<DashBoardViewmodel>()
    val dashBoardState by dashedBoardViewModel.state.collectAsState()
    var isExtraLineVisible by remember { mutableStateOf(false) }
    var selectedSegment by remember { mutableStateOf("WEEK") }
    var totalEntries by remember { mutableStateOf(listOf<ScaleEntry>()) }
    var graphLines: GraphLine by remember(isExtraLineVisible) {
        mutableStateOf(
            dashBoardState.last7DaysEntries.toWeightGraphPoints(),
        )
    }
    var isAddEntryModalVisible by remember { mutableStateOf(false) }

    fun filterEntriesBySegment(
        entries: List<ScaleEntry>,
        segment: String
    ): List<ScaleEntry> {
        val now = LocalDateTime.now()
        val zone = ZoneId.of("America/Los_Angeles")
        val nowEpoch = now.atZone(zone).toInstant().toEpochMilli()
        return when (segment) {
            "WEEK" -> dashBoardState.last7DaysEntries

            "MONTH" -> dashBoardState.last30DaysEntries

            "YEAR" -> {
                val yearAgo = now.minusDays(364).atZone(zone).toInstant().toEpochMilli()
                entries.filter { it.entry.entryTimestamp in yearAgo..nowEpoch }
            }

            "ANY", "TOTAL" -> entries
            else -> entries
        }
    }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primary),
    ) {
        GraphView(
            modifier = Modifier
                .fillMaxHeight(0.5f)
                .fillMaxWidth(),
            graphLines = listOf(dashBoardState.totalEntries.toWeightGraphPoints()),
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
                val filtered = filterEntriesBySegment(totalEntries, segment)
                graphLines = filtered.toWeightGraphPoints()
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
                    totalEntries = (totalEntries + entries).sortedBy { it.entry.entryTimestamp }
                    val filtered = filterEntriesBySegment(totalEntries, selectedSegment)
                    graphLines = filtered.toWeightGraphPoints()
                }
            },
        )
    }
}
