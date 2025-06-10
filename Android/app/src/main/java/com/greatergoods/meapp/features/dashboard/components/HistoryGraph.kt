package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.components.chart.GraphView
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import com.greatergoods.meapp.theme.MeAppTheme

@Composable
fun HistoryGraph() {
    List(50) { index ->
        ScaleEntry(
            entry = EntryEntity(
                id = index.toLong(),
                accountId = "account_$index",
                entryTimestamp = "2025-06-10T10:${index % 60}:00Z",
                serverTimestamp = null,
                opTimestamp = null,
                operationType = "CREATE",
                deviceType = "SCALE",
                deviceId = "device_$index",
                attempts = 0,
                isSynced = false,
            ),
            scale = ScaleEntryWithMetrics(
                scaleEntry = BodyScaleEntryEntity(
                    id = index.toLong(),
                    weight = 60 + index, // in grams
                    bodyFat = 200 + index, // in deci-percent
                    muscleMass = 200 + index,
                    water = 400 + index,
                    bmi = 2200 + index, // e.g., 22.00
                    source = "manual",
                ),
                scaleEntryMetric = null,
            ),
        )
    }

    var isExtraLineVisible by remember { mutableStateOf(false) }
    var selectedSegment by remember { mutableStateOf("MONTH") }

    val baseGraphLine = GraphLine(
        "Line 1",
        listOf(
            GraphPoint(Label(1, "Jan"), Label(2, "Feb")),
            GraphPoint(Label(3, "Mar"), Label(4, "Apr")),
            GraphPoint(Label(5, "May"), Label(6, "Jun")),
            GraphPoint(Label(7, "Jul"), Label(8, "Aug")),
            GraphPoint(Label(9, "Sep"), Label(10, "Oct")),
            GraphPoint(Label(11, "Nov"), Label(12, "Dec")),
        ),
    )
    val extraGraphLine = GraphLine(
        "Line 2",
        listOf(
            GraphPoint(Label(1, "Jan"), Label(1.5f, "Feb")),
            GraphPoint(Label(3, "Mar"), Label(2.5f, "Apr")),
            GraphPoint(Label(5, "May"), Label(3.5f, "Jun")),
            GraphPoint(Label(7, "Jul"), Label(4.5f, "Aug")),
            GraphPoint(Label(9, "Sep"), Label(5.5f, "Oct")),
            GraphPoint(Label(11, "Nov"), Label(6.5f, "Dec")),
        ),
    )
    val graphLines = remember(isExtraLineVisible) {
        if (isExtraLineVisible) listOf(baseGraphLine, extraGraphLine) else listOf(baseGraphLine)
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .background(MeAppTheme.colorScheme.primary),
    ) {
        GraphView(
            graphLines = graphLines,
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
            onSelect = { selectedSegment = it },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = MeAppTheme.spacing.md),
        )
    }
}
