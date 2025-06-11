package com.greatergoods.meapp.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntryWithMetrics
import com.greatergoods.meapp.features.common.components.chart.GraphView
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.toWeightGraphPoints
import com.greatergoods.meapp.theme.MeAppTheme
import java.time.ZoneOffset
import java.time.ZonedDateTime
import android.util.Log

private val weightList = (50..70).toList()
val sampleScaleEntry = List(20) { index ->
    val timestamp = ZonedDateTime.of(2025, 6, 10, 10, index % 60, 0, 0, ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    ScaleEntry(
        entry = EntryEntity(
            id = index.toLong(),
            accountId = "account_$index",
            entryTimestamp = timestamp.toString(),
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
                weight = weightList.random(), // in grams
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

@Composable
fun HistoryGraph() {

    var isExtraLineVisible by remember { mutableStateOf(false) }
    var selectedSegment by remember { mutableStateOf("MONTH") }

    val graphLines = remember(isExtraLineVisible) {
        sampleScaleEntry.toWeightGraphPoints()
    }

    Log.i("CHECKING", graphLines.points.map { it.y.value }.toString())
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .background(MeAppTheme.colorScheme.primary),
    ) {
        GraphView(
            graphLines = listOf(graphLines),
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
            onSelect = { selectedSegment = it },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = MeAppTheme.spacing.md),
        )
    }
}
